package com.example.travelagency.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BookingRequest(@NotBlank String customerName, @Min(1) long tourId, @Min(1) int seats) {
}
