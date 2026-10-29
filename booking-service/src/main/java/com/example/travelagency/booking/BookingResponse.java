package com.example.travelagency.booking;

public record BookingResponse(long id, String customerName, long tourId, int seats) {
}
