package com.campsite.booking.repository;

import com.campsite.booking.entity.Booking;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BookingRepository extends R2dbcRepository<Booking, Long> {
    Mono<Booking> findBookingByBookingId(UUID bookingId);
    Mono<Void> deleteBookingByBookingId(UUID bookingId);

}
