package com.campsite.booking.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    public Booking(String name, String email, UUID bookingId, LocalDate start, LocalDate end) {
        this.name = name;
        this.email = email;
        this.bookingId = bookingId;
        this.start = start;
        this.end = end;
    }

    @Id
    private Long id;

    private UUID bookingId;

    @NotNull
    private String name;
    @NotNull
    private String email;

    @Transient
    private LocalDate start;

    @Transient
    private LocalDate end;
}
