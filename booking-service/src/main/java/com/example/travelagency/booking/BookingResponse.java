package com.example.travelagency.booking;

import com.example.travelagency.contract.BookingEvent.Status;

public record BookingResponse(long id, String customerName, long tourId, int seats, Status status) {
}
