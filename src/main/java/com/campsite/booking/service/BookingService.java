package com.campsite.booking.service;

import com.campsite.booking.dto.AvailabilityQueryResponse;
import com.campsite.booking.dto.BookingRequest;
import com.campsite.booking.dto.BookingResponse;
import com.campsite.booking.dto.DeletionResponse;
import com.campsite.booking.dto.UpdateRequest;
import com.campsite.booking.entity.Booking;
import com.campsite.booking.entity.BookingDate;
import com.campsite.booking.repository.BookingDateRepository;
import com.campsite.booking.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BookingService {
    private final BookingRepository bookingRepo;
    private final BookingDateRepository bookingDateRepo;

    @Autowired
    public BookingService(BookingRepository bookingRepo, BookingDateRepository bookingDateRepo) {
        this.bookingRepo = bookingRepo;
        this.bookingDateRepo = bookingDateRepo;
    }

    @Transactional(readOnly = true)
    public Mono<AvailabilityQueryResponse> getAvailability(final LocalDate start, final LocalDate end) {
        LocalDate maxEndDate = LocalDate.now().plusMonths(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        LocalDate startDate = start.isBefore(tomorrow) ? tomorrow : start.plusDays(0);
        LocalDate endDate = (end == null || end.isAfter(maxEndDate)) ? maxEndDate : end.plusDays(0);

        log.info(String.format("Query site availability from %s to %s", startDate, endDate));

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date need to be early or the same as the end date.");
        }

        return bookingDateRepo.findBookingDateByBookedDateBetweenOrderByBookedDateAsc(startDate, endDate)
            .collectList()
            .map(bookedDates -> getAvailabilityQueryResponse(startDate, endDate, bookedDates));
    }

    private AvailabilityQueryResponse getAvailabilityQueryResponse(final LocalDate start,
                                                                   final LocalDate end,
                                                                   final List<BookingDate> bookedDates) {
        final Set<LocalDate> bookedDatesSet = bookedDates.stream().map(BookingDate::getBookedDate).collect(Collectors.toSet());
        final List<LocalDate> availableDates = start.datesUntil(end.plusDays(1))
            .filter(d -> !bookedDatesSet.contains(d))
            .collect(Collectors.toList());
        return new AvailabilityQueryResponse(availableDates);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Mono<BookingResponse> book(Mono<BookingRequest> request) {
        return request
            .doOnNext(req -> validateBookingDates(req.getStart(), req.getEnd()))
            .map(this::getBooking)
            .flatMap(booking -> bookingRepo.save(booking))
            .flatMap(this::saveBookingDates)
            .map(bookingId -> new BookingResponse(bookingId));
    }

    private Booking getBooking(final BookingRequest req) {
        return new Booking(req.getName(), req.getEmail(), UUID.randomUUID(), req.getStart(), req.getEnd());
    }

    private Mono<UUID> saveBookingDates(final Booking booking) {
        List<BookingDate> bookingDates = getBookingDates(booking.getId(), booking.getStart(), booking.getEnd());
        return bookingDateRepo.saveAll(bookingDates).collectList().map( dates -> booking.getBookingId());
    }

    private List<BookingDate> getBookingDates(final Long bookingId, final LocalDate start, final LocalDate end) {
        List<BookingDate> res = new ArrayList<>();
        start.datesUntil(end.plusDays(1)).forEach( d -> res.add(new BookingDate(bookingId, d)) );
        return res;
    }

    private void validateBookingDates(final LocalDate start, final LocalDate end) {
        LocalDate now = LocalDate.now();
        if (start.isBefore(now.plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                              String.format("The campsite can be reserved minimum 1 day(s) ahead of arrival, but you requested on %s", start));
        }
        if (end.isAfter(now.plusMonths(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                              String.format("The campsite can be reserved up to 1 month in advance, but you requested on %s", start));
        }
        if (start.isAfter(end) || start.plusDays(2).isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                              String.format("Campsite can be reserved for max 3 days, but you requested start date is %s, and end date is %s", start, end));
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Mono<BookingResponse> update(final UUID id, final Mono<UpdateRequest> request) {
        return bookingRepo.findBookingByBookingId(id)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking is not found")))
            .zipWith(request)
            .flatMap(this::updateBookingAndBookingDates)
            .map(bookingId -> new BookingResponse(bookingId));
    }

    private Mono<? extends UUID> updateBookingAndBookingDates(Tuple2<Booking,UpdateRequest> bookingWithUpdate) {
        final Booking booking = bookingWithUpdate.getT1();
        final UpdateRequest update = bookingWithUpdate.getT2();
        boolean bookingUpdated = false;
        if (update.getName() != null) {
            booking.setName(update.getName());
            bookingUpdated = true;
        }
        if (update.getEmail() != null) {
            booking.setEmail(update.getEmail());
            bookingUpdated = true;
        }

        final LocalDate updateStart = update.getStart();
        final LocalDate updateEnd = update.getEnd();
        if (bookingUpdated) {
            return bookingRepo.save(booking).flatMap(updatedBooking -> updateBookingDates(updatedBooking, updateStart, updateEnd));
        }
        else {
            return updateBookingDates(booking, updateStart, updateEnd);
        }
    }

    private Mono<UUID> updateBookingDates(final Booking booking, final LocalDate newStart, final LocalDate newEnd) {
        if (newStart != null || newEnd != null) {
            //need to update booking dates: old dates are deleted, and new dates are inserted.
            return bookingDateRepo.findBookingDateByBookingIdOrderByBookedDateAsc(booking.getId())
                .collectList()
                .map(dates -> getUpdatedBookingDates(booking, dates, newStart, newEnd))
                .flatMap(updatedDate -> deleteOldThenStoreNewBookingDates(updatedDate, booking));
        }
        else {
            return Mono.just(booking.getBookingId());
        }
    }

    private Mono<UUID> deleteOldThenStoreNewBookingDates(final List<BookingDate> newBookingDates, final Booking booking) {
        return bookingDateRepo.deleteAllByBookingId(booking.getId())
            .thenMany(bookingDateRepo.saveAll(newBookingDates))
            .collectList()
            .map(dates -> booking.getBookingId());
    }


    private List<BookingDate> getUpdatedBookingDates(final Booking booking,
                                                     final List<BookingDate> existingDates,
                                                     final LocalDate newStart,
                                                     final LocalDate newEnd) {
        final LocalDate updateStart = (newStart == null) ?
            existingDates.get(0).getBookedDate().plusDays(0) :
            newStart.plusDays(0);
        final LocalDate updateEnd = (newEnd == null) ?
            existingDates.get(existingDates.size() - 1).getBookedDate().plusDays(0) :
            newEnd.plusDays(0);
        validateBookingDates(updateStart, updateEnd);
        return getBookingDates(booking.getId(), updateStart, updateEnd);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Mono<DeletionResponse> delete(final UUID id) {
        return bookingRepo.findBookingByBookingId(id)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking is not found")))
            .flatMap(booking -> bookingRepo.deleteBookingByBookingId(id))
            .thenReturn(new DeletionResponse(id));
    }
}
