package com.campsite.booking.repos;

import com.campsite.booking.entity.BookingDate;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;


public interface BookingDateRepository extends R2dbcRepository<BookingDate, Long> {
    Flux<BookingDate> findBookingDateByBookedDateBetweenOrderByBookedDateAsc(LocalDate start, LocalDate enDate);
    Flux<BookingDate> findBookingDateByBookingIdOrderByBookedDateAsc(Long bookingId);
    Mono<Void> deleteAllByBookingId(Long bookingId);
}
