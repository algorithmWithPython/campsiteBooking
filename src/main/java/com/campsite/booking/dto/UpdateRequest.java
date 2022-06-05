package com.campsite.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.Email;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class UpdateRequest {
    String name;
    @Email(message="Not a valid Email", regexp = "^(.+)@(\\S+)$")
    String email;
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate start;
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate end;
}