package com.campsite.booking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDate {
    public BookingDate(Long bookingId, /*UUID bookingUUID,*/ LocalDate bookedDate) {
        this.bookingId = bookingId;
        this.bookedDate = bookedDate;
    }

    @Id
    private Long id;

    private Long bookingId;
    private LocalDate bookedDate;
}
