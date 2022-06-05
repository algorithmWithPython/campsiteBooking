package com.campsite.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.Data;

import java.util.Date;

@Data
public class AvailabilityQueryRequest {
    @JsonFormat(shape=Shape.STRING, pattern = "yyyy-MM-dd")
    Date start;
    @JsonFormat(shape=Shape.STRING, pattern = "yyyy-MM-dd")
    Date end;
}
