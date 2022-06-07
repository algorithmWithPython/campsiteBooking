package com.campsite.booking.controller;

import com.campsite.booking.dto.BookingRequest;
import com.campsite.booking.dto.UpdateRequest;
import com.campsite.booking.entity.Booking;
import com.campsite.booking.entity.BookingDate;
import com.campsite.booking.exception.GlobalErrorAttributes;
import com.campsite.booking.repository.BookingDateRepository;
import com.campsite.booking.repository.BookingRepository;
import com.campsite.booking.service.BookingService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@WebFluxTest
@Import( {GlobalErrorAttributes.class, BookingService.class})
public class BookingControllerTest {
    @MockBean
    private BookingDateRepository bookingDateRepository;
    @MockBean
    private BookingRepository bookingRepository;

    @Autowired
    private WebTestClient testClient;

    private LocalDate currentDate = LocalDate.now();

    @Captor
    ArgumentCaptor<Booking> bookingCaptor;

    @Captor
    ArgumentCaptor<List<BookingDate>> bookingDate;

    @BeforeEach
    public void setUp() {
        testClient = testClient.mutate().responseTimeout(Duration.ofSeconds(15))
                .build();
        when(bookingRepository.save(any(Booking.class)))
            .thenAnswer((Answer<Mono<Booking>>) invocation -> {
                Booking booking = invocation.getArgument(0, Booking.class);
                return Mono.just(booking);
            });

        when(bookingDateRepository.saveAll(anyList()))
            .thenAnswer((Answer<Flux<BookingDate>>) invocation -> {
                List<BookingDate> bookingDates = (List<BookingDate>) invocation.getArgument(0, List.class);
                return Flux.fromIterable(bookingDates);
            });
    }

    @Test
    @DisplayName("Query Availability return available dates")
    public void queryAvailabilitySuccessful() {
        when(bookingDateRepository.findBookingDateByBookedDateBetweenOrderByBookedDateAsc(currentDate.plusDays(1), currentDate.plusDays(5)))
            .thenReturn(Flux.just(new BookingDate(1L, currentDate.plusDays(2)),
                                  new BookingDate(1L, currentDate.plusDays(3))));
        testClient
            .get()
            .uri(ruiBuilder -> ruiBuilder
                .path("/booking/api/v1/availability")
                .queryParam("start", currentDate.plusDays(1))
                .queryParam("end", currentDate.plusDays(5))
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.availableDates").isArray()
            .jsonPath("$.availableDates.length()").isEqualTo(3)
            .jsonPath("$.availableDates[0]").isEqualTo(getDateAsString(currentDate.plusDays(1)))
            .jsonPath("$.availableDates[1]").isEqualTo(getDateAsString(currentDate.plusDays(4)))
            .jsonPath("$.availableDates[2]").isEqualTo(getDateAsString(currentDate.plusDays(5)));
    }

    private String getDateAsString(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    @Test
    @DisplayName("Query Availability with wide range")
    public void queryAvailabilityWideRange() {
        when(bookingDateRepository.findBookingDateByBookedDateBetweenOrderByBookedDateAsc(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Flux.just(new BookingDate(1L, currentDate.plusDays(2))));

        long expectedAvailableDates = DAYS.between(currentDate.plusDays(1), currentDate.plusMonths(1).plusDays(1)) - 1;
        testClient
            .get()
            .uri(ruiBuilder -> ruiBuilder
                .path("/booking/api/v1/availability")
                .queryParam("start", currentDate.minusDays(5))
                .queryParam("end", currentDate.plusMonths(2))
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.availableDates.length()").isEqualTo(expectedAvailableDates)
            .jsonPath("$.availableDates[0]").isEqualTo(getDateAsString(currentDate.plusDays(1)))
            .jsonPath("$.availableDates[1]").isEqualTo(getDateAsString(currentDate.plusDays(3)));

        verify(bookingDateRepository).findBookingDateByBookedDateBetweenOrderByBookedDateAsc(currentDate.plusDays(1), currentDate.plusMonths(1));
    }

    @Test
    @DisplayName("Query Availability with start date only")
    public void queryAvailabilityWithStartDayOnly() {
        when(bookingDateRepository.findBookingDateByBookedDateBetweenOrderByBookedDateAsc(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(Flux.empty());

        long expectedAvailableDates = DAYS.between(currentDate.plusDays(1), currentDate.plusMonths(1).plusDays(1));
        testClient
            .get()
            .uri(ruiBuilder -> ruiBuilder
                .path("/booking/api/v1/availability")
                .queryParam("start", currentDate.minusDays(1))
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.availableDates.length()").isEqualTo(expectedAvailableDates);

        verify(bookingDateRepository).findBookingDateByBookedDateBetweenOrderByBookedDateAsc(currentDate.plusDays(1), currentDate.plusMonths(1));
    }

    @Test
    @DisplayName("Query Availability failed without start date")
    public void queryAvailabilityWithoutStartDayOnly() {
        testClient
            .get()
            .uri(ruiBuilder -> ruiBuilder
                .path("/booking/api/v1/availability")
                .queryParam("end", currentDate)
                .build())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.status").isEqualTo(400);
    }

    @Test
    @DisplayName("booking is successful")
    public void BookingTest() {

        BookingRequest booking = createBookingRequest(1, 2);
        testClient
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.bookingId")
            .value(bookingId -> {
                verify(bookingRepository).save(bookingCaptor.capture());
                verify(bookingDateRepository).saveAll(bookingDate.capture());
                assertEquals(UUID.fromString(bookingId.toString()), bookingCaptor.getValue().getBookingId());
                assertEquals(2, bookingDate.getValue().size());
            });
    }

    private BookingRequest createBookingRequest(int daysAfterTodayAsStart, int daysAfterTodayAsEnd) {
        BookingRequest booking = new BookingRequest("name",
                                                    "e@e",
                                                    currentDate.plusDays(daysAfterTodayAsStart),
                                                    currentDate.plusDays(daysAfterTodayAsEnd));
        return booking;
    }

    @Test
    @DisplayName("booking fails due to start date is too early")
    public void BookingTestFailDueToStartDate() {
        BookingRequest booking = createBookingRequest(0, 1);
        testClient
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("booking fails due to the booking is more than 3 days")
    public void BookingTestFailDueToTooManyDays() {
        BookingRequest booking = createBookingRequest(2, 10);
        testClient
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("booking fails due to the end day is too far away")
    public void BookingTestFailDueToEndDate() {
        BookingRequest booking = new BookingRequest("name",
                                                    "e@e",
                                                    currentDate.plusMonths(1),
                                                    currentDate.plusMonths(1).plusDays(1));
        testClient
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking))
            .exchange()
            .expectStatus().isBadRequest();
    }


    @Test
    @DisplayName("booking fails due to missing name")
    public void BookingTestFailWithoutName() {
        BookingRequest booking = new BookingRequest(null, "e@e", currentDate.plusDays(1), currentDate.plusDays(1));
        testClient
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking))
            .exchange()
            .expectStatus().isBadRequest();
    }

    private Booking createBooking(long bookingId, UUID bookingUUID, int daysAfterTodayAsStart, int daysAfterTodayAsEnd) {
        Booking booking = new Booking(bookingId,
                                      bookingUUID,
                                      "name",
                                      "e@e",
                                      currentDate.plusDays(daysAfterTodayAsStart),
                                      currentDate.plusDays(daysAfterTodayAsEnd));
        return booking;
    }

    private BookingDate createBookingDate(long bookingDateId, long bookingId, int daysAfterToday) {
        return new BookingDate(bookingDateId, bookingId, currentDate.plusDays(daysAfterToday));
    }

    @Test
    @DisplayName("update is successful")
    public void updateBookingTest() {
        long bookingId = 1;
        UUID bookingUUID = UUID.randomUUID();

        //mock existing booking
        Booking booking = createBooking(bookingId, bookingUUID, 1, 1);
        when(bookingRepository.findBookingByBookingId(bookingUUID)).thenReturn(Mono.just(booking));

        //mock existing booking date (only 1 day)
        BookingDate bookingDate = createBookingDate(1L, bookingId, 1);
        when(bookingDateRepository.findBookingDateByBookingIdOrderByBookedDateAsc(bookingId))
            .thenReturn(Flux.just(bookingDate));

        when(bookingDateRepository.deleteAllByBookingId(bookingId)).thenReturn(Mono.empty());

        String newName = "newName";
        String newEmail = "new@email";
        UpdateRequest update = createUpdateRequest(newName, newEmail, 1, 3);
        testClient
            .patch()
            .uri("/booking/api/v1/update/{id}", bookingUUID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(update))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.bookingId")
            .value(resultId -> {
                verify(bookingRepository).save(this.bookingCaptor.capture());
                Booking updatedBooking = this.bookingCaptor.getValue();
                assertEquals(newName, updatedBooking.getName());
                assertEquals(newEmail, updatedBooking.getEmail());
                verify(bookingDateRepository).saveAll(this.bookingDate.capture());
                final List<BookingDate> bookingDates = this.bookingDate.getValue();
                assertEquals(3, bookingDates.size());
                assertEquals(currentDate.plusDays(1), bookingDates.get(0).getBookedDate());
                assertEquals(currentDate.plusDays(2), bookingDates.get(1).getBookedDate());
                assertEquals(currentDate.plusDays(3), bookingDates.get(2).getBookedDate());
            });
    }

    @Test
    @DisplayName("update is successful only with name and email")
    public void updateBookingWithoutDatesTest() {
        long bookingId = 1;
        UUID bookingUUID = UUID.randomUUID();

        //mock existing booking
        Booking booking = createBooking(bookingId, bookingUUID, 1, 1);
        when(bookingRepository.findBookingByBookingId(bookingUUID)).thenReturn(Mono.just(booking));

        String newName = "newName";
        String newEmail = "new@email";
        UpdateRequest update = new UpdateRequest(newName, newEmail, null, null);
        testClient
            .patch()
            .uri("/booking/api/v1/update/{id}", bookingUUID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(update))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.bookingId")
            .value(resultId -> {
                verify(bookingRepository).save(this.bookingCaptor.capture());
                Booking updatedBooking = this.bookingCaptor.getValue();
                assertEquals(newName, updatedBooking.getName());
                assertEquals(newEmail, updatedBooking.getEmail());
                assertEquals(currentDate.plusDays(1), updatedBooking.getStart());
                assertEquals(currentDate.plusDays(1), updatedBooking.getEnd());
                verify(bookingDateRepository, never()).findBookingDateByBookingIdOrderByBookedDateAsc(anyLong());
            });
    }

    @Test
    @DisplayName("update is successful only on Dates")
    public void updateTestOnlyDates() {
        long bookingId = 1;
        UUID bookingUUID = UUID.randomUUID();

        //mock existing booking
        Booking booking = createBooking(bookingId, bookingUUID, 1, 1);
        when(bookingRepository.findBookingByBookingId(bookingUUID)).thenReturn(Mono.just(booking));

        //mock existing booking date (only 1 day)
        BookingDate bookingDate = createBookingDate(1L, bookingId, 1);
        when(bookingDateRepository.findBookingDateByBookingIdOrderByBookedDateAsc(bookingId))
            .thenReturn(Flux.just(bookingDate));

        when(bookingDateRepository.deleteAllByBookingId(bookingId)).thenReturn(Mono.empty());

        UpdateRequest update = createUpdateRequest(null, null, 1, 3);
        testClient
            .patch()
            .uri("/booking/api/v1/update/{id}", bookingUUID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(update))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.bookingId")
            .value(resultId -> {
                verify(bookingRepository, never()).save(any(Booking.class));
                verify(bookingDateRepository).saveAll(this.bookingDate.capture());
                final List<BookingDate> bookingDates = this.bookingDate.getValue();
                assertEquals(3, bookingDates.size());
                assertEquals(currentDate.plusDays(1), bookingDates.get(0).getBookedDate());
                assertEquals(currentDate.plusDays(2), bookingDates.get(1).getBookedDate());
                assertEquals(currentDate.plusDays(3), bookingDates.get(2).getBookedDate());
            });
    }

    @NotNull private UpdateRequest createUpdateRequest(String name, String email, int daysAfterTodayAsStart, int daysAfterTodayAsEnd) {
        return new UpdateRequest(name,
                                 email,
                                 currentDate.plusDays(daysAfterTodayAsStart),
                                 currentDate.plusDays(daysAfterTodayAsEnd));
    }

    @Test
    @DisplayName("update fails due to the end day is too advanced")
    public void updateTestFailed() {
        long bookingId = 1L;
        UUID bookingUUID = UUID.randomUUID();

        Booking bookingData = createBooking(bookingId, bookingUUID, 1, 3);
        when(bookingRepository.findBookingByBookingId(bookingUUID)).thenReturn(Mono.just(bookingData));

        when(bookingDateRepository.findBookingDateByBookingIdOrderByBookedDateAsc(1L))
            .thenReturn(Flux.just(createBookingDate(1L, bookingId, 1)));

        UpdateRequest update = createUpdateRequest(null, null, 1, 4);
        testClient
            .patch()
            .uri("/booking/api/v1/update/{id}", bookingUUID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(update))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("update fails due to the booking does not exist")
    public void updateTestFailedDueToMissingBooking() {
        UUID bookingId = UUID.randomUUID();

        when(bookingRepository.findBookingByBookingId(bookingId)).thenReturn(Mono.empty());

        UpdateRequest update = createUpdateRequest(null, null, 1, 1);
        testClient
            .patch()
            .uri("/booking/api/v1/update/{id}", bookingId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(update))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("cancellation is successful")
    public void CancelTest() {
        UUID bookingId = UUID.randomUUID();

        Booking bookingData = createBooking(1L, bookingId, 1, 2);
        when(bookingRepository.findBookingByBookingId(bookingId)).thenReturn(Mono.just(bookingData));
        when(bookingRepository.deleteBookingByBookingId(bookingId)).thenReturn(Mono.empty());

        testClient
            .delete()
            .uri("/booking/api/v1/cancel/{id}", bookingId.toString())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.bookingId").isEqualTo(bookingId.toString());
    }

    @Test
    @DisplayName("cancellation fails due to missing the booking")
    public void cancelTestFailed() {
        UUID bookingId = UUID.randomUUID();

        when(bookingRepository.findBookingByBookingId(bookingId)).thenReturn(Mono.empty());

        testClient
            .delete()
            .uri("/booking/api/v1/cancel/{id}", bookingId.toString())
            .exchange()
            .expectStatus().isNotFound();
    }

}
