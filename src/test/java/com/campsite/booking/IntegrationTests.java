package com.campsite.booking;

import com.campsite.booking.dto.AvailabilityQueryResponse;
import com.campsite.booking.dto.BookingRequest;
import com.campsite.booking.dto.BookingResponse;
import com.campsite.booking.dto.DeletionResponse;
import com.campsite.booking.dto.UpdateRequest;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@ContextConfiguration(initializers = {IntegrationTests.Initializer.class})
class IntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient testClient;

    @ClassRule
    @Container
    public static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:12.11")
        .withDatabaseName("postgres")
        .withUsername("camp")
        .withPassword("camp123");

    static class Initializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                "spring.datasource.url=" + postgres.getJdbcUrl(),
                "spring.datasource.username=" + postgres.getUsername(),
                "spring.datasource.password=" + postgres.getPassword(),
                "spring.flyway.url=" + postgres.getJdbcUrl(),
                "spring.flyway.user=" + postgres.getUsername(),
                "spring.flyway.password=" + postgres.getPassword(),
                "spring.r2dbc.url=r2dbc:postgresql://localhost:" + postgres.getFirstMappedPort() + "/postgres",
                "spring.r2dbc.username=" + postgres.getUsername(),
                "spring.r2dbc.password=" + postgres.getPassword()
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @Test
    void operationsTest() {

        LocalDate currentDate = LocalDate.now();

        //initial query, all 5 days are available
        AvailabilityQueryResponse queryResponse = getAvailabilityQueryResponse(testClient, currentDate);
        System.out.println(queryResponse);
        assertEquals(5, queryResponse.getAvailableDates().size());

        //make a booking for 2 days
        BookingRequest booking = new BookingRequest("name", "e@e", currentDate.plusDays(2), currentDate.plusDays(3));
        BookingResponse bookingResponse = testClient
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(BookingResponse.class)
            .returnResult()
            .getResponseBody();
        System.out.println(bookingResponse);

        UUID bookingId = bookingResponse.getBookingId();

        //query availability, now 5-2=3 days are available
        AvailabilityQueryResponse queryResponse2 = getAvailabilityQueryResponse(testClient, currentDate);
        System.out.println(queryResponse2);
        assertEquals(3, queryResponse2.getAvailableDates().size());

        //make another booking for 3 days, which overlaps with the previous booking(fail)
        BookingRequest booking2 = new BookingRequest("name", "e@e", currentDate.plusDays(3), currentDate.plusDays(5));
        testClient
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking2))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        //query availability, 3 days are still available
        AvailabilityQueryResponse queryResponse3 = getAvailabilityQueryResponse(testClient, currentDate);
        System.out.println(queryResponse3);
        assertEquals(3, queryResponse3.getAvailableDates().size());

        //update the booking from 3 days to 3 days
        UpdateRequest updateRequest = new UpdateRequest("name2", "f@f", currentDate.plusDays(1), null);
        BookingResponse updateResponse = testClient
            .patch()
            .uri("/booking/api/v1/update/{id}", bookingId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(updateRequest))
            .exchange()
            .expectStatus().isOk()
            .expectBody(BookingResponse.class)
            .returnResult()
            .getResponseBody();

        assertEquals(bookingId, updateResponse.getBookingId());

        //query availability, now 5=3=2 days are available
        AvailabilityQueryResponse queryResponse4 = getAvailabilityQueryResponse(testClient, currentDate);
        System.out.println(queryResponse4);
        assertEquals(2, queryResponse4.getAvailableDates().size());

        //delete the booking
        DeletionResponse deletionResponse = testClient
            .delete()
            .uri("/booking/api/v1/cancel/{id}", bookingId.toString())
            .exchange()
            .expectStatus().isOk()
            .expectBody(DeletionResponse.class)
            .returnResult()
            .getResponseBody();

        assertEquals(bookingId, deletionResponse.getBookingId());

        //query availability, 5 days are available.
        AvailabilityQueryResponse queryResponse5 = getAvailabilityQueryResponse(testClient, currentDate);
        System.out.println(queryResponse5);
        assertEquals(5, queryResponse5.getAvailableDates().size());
    }

    //Test concurrent booking 3 times
    @Test
    void concurrentBookingTest() {
        IntStream.range(0, 3).forEach(n -> concurrentBooking());
    }

    //3 bookings at the same time, all of them are overlapping, there should be only one successful booking.
    private void concurrentBooking() {

        List<String> bookings = Flux.range(1, 3)
            .parallel(3)
            .runOn(Schedulers.parallel())
            .flatMap(i -> {
                System.out.println("do booking in thread" + Thread.currentThread());
                Mono<String> res = createBooking(i).onErrorReturn("");
                return res;
            })
            .sequential()
            .collectList()
            .block();

        List<String> bookingIds = bookings.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
        assertEquals(1, bookingIds.size());
        cancelBooking(bookingIds.get(0));
    }

    private void cancelBooking(String bookingId) {

        DeletionResponse response = WebClient.builder().baseUrl("http://localhost:" + port + "/").build()
            .delete().uri(builder -> builder
                .path("/booking/api/v1/cancel/{id}")
                .build(bookingId.toString()))
            .retrieve()
            .bodyToMono(DeletionResponse.class)
            .block();
        assertEquals(bookingId, response.getBookingId().toString());
    }

    private Mono<String> createBooking(int i) {
        LocalDate currentDate = LocalDate.now();
        BookingRequest booking = new BookingRequest("name", "e@e", currentDate.plusDays(i), currentDate.plusDays(i + 2));

        return WebClient.builder().baseUrl("http://localhost:" + port + "/").build()
            .post()
            .uri("/booking/api/v1/book")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(booking))
            .retrieve()
            .onStatus(HttpStatus::is5xxServerError, error -> {
                System.out.println("booking get error in thread: " + Thread.currentThread());
                return Mono.error(new Exception("booking error"));
            })
            .bodyToMono(BookingResponse.class)
            .map(response -> {
                System.out.println("booking is successful in thread: " + Thread.currentThread());
                return response.getBookingId().toString();
            });
    }

    private AvailabilityQueryResponse getAvailabilityQueryResponse(WebTestClient testClient, LocalDate currentDate) {
        return testClient.
            get()
            .uri(builder -> builder
                .path("/booking/api/v1/availability")
                .queryParam("start", currentDate.plusDays(1))
                .queryParam("end", currentDate.plusDays(5))
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody(AvailabilityQueryResponse.class)
            .returnResult()
            .getResponseBody();
    }
}
