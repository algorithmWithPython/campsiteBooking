package com.campsite.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class BookingRequest {
    @NotNull(message="Name cannot be missing or empty")
    String name;
    @NotNull(message="Email cannot be missing or empty")
    @Email(message="Not a valid Email", regexp = "^(.+)@(\\S+)$")
    String email;
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd")
    @NotNull(message="Start date cannot be missing or empty")
    LocalDate start;
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd")
    @NotNull(message="End date cannot be missing or empty")
    LocalDate end;
}
