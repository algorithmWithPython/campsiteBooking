package com.campsite.booking.controller;

import com.campsite.booking.dto.AvailabilityQueryResponse;
import com.campsite.booking.dto.BookingRequest;
import com.campsite.booking.dto.BookingResponse;
import com.campsite.booking.dto.DeletionResponse;
import com.campsite.booking.dto.UpdateRequest;
import com.campsite.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/booking/api/v1")
@Slf4j
@RequiredArgsConstructor
public class BookingController {
    private final BookingService service;

    @GetMapping(path = "/availability",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query available dates to book the site",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of available dates between tomorrow and one month later",
                content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = AvailabilityQueryResponse.class))}
            )}
    )
    @ResponseStatus(HttpStatus.OK)
    public Mono<AvailabilityQueryResponse> queryAvailability(
        @RequestParam(value = "start")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate start,
        @RequestParam(value = "end", required = false)
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate end
    ) {
        return service.getAvailability(start, end);
    }

    @PostMapping(path = "/book",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Make a site booking",
        responses = {
            @ApiResponse(responseCode = "201", description = "Booking is successful, the booking ID is returned",
                content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = BookingResponse.class))}),
            @ApiResponse(responseCode = "400", description = "Booking failed due to invalid booking request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server side error, for example, conflict with other bookings", content = @Content),
        })
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<BookingResponse> book(
        @Valid @RequestBody Mono<BookingRequest> request) {
        return service.book(request);
    }

    @PatchMapping(path = "/update/{id}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a booking by providing the booking id",
        responses = {
            @ApiResponse(responseCode = "200", description = "Booking is updated successfully",
                content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = BookingResponse.class))}),
            @ApiResponse(responseCode = "400", description = "Update failed due to invalid update request", content = @Content),
            @ApiResponse(responseCode = "404", description = "The booking does not exist", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server side error, for example, conflict with other bookings", content = @Content),
        })
    @ResponseStatus(HttpStatus.OK)
    public Mono<BookingResponse> update(
        @PathVariable("id") String id,
        @Valid @RequestBody Mono<UpdateRequest> booking) {
        UUID uuid = getUUID(id);
        log.info(String.format("Update booking: %s", uuid));
        return service.update(uuid, booking);
    }

    private UUID getUUID(String id) {
        try {
            return UUID.fromString(id); //throws IllegalArgumentException
        }
        catch (Exception e) {
            log.error("Invalid UUID.", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID", e);
        }
    }

    @DeleteMapping(path = "/cancel/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancel a booking by its booking id",
        responses = {
            @ApiResponse(responseCode = "200", description = "Booking is successfully cancelled",
                content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = BookingResponse.class))}),
            @ApiResponse(responseCode = "404", description = "The booking does not exist", content = @Content),
        })
    @ResponseStatus(HttpStatus.OK)
    public Mono<DeletionResponse> cancel(
        @PathVariable("id") String id
    ) {
        UUID uuid = getUUID(id); //throws IllegalArgumentException
        log.info(String.format("Delete booking: %s", uuid));
        return service.delete(uuid);
    }
}
