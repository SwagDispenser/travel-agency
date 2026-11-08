package com.example.travelagency.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingRequest(@NotBlank @Size(max = 120) String customerName, @Min(1) long tourId, @Min(1) int seats) {
}
