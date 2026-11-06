package com.example.travelagency.contract;

import java.time.Instant;

public record BookingEvent(String eventId, Type type, long bookingId, String customerName,
                           long tourId, int seats, Status status, Instant occurredAt) {
    public enum Type { CREATED, CONFIRMED, CANCELLED }
    public enum Status { NEW, CONFIRMED, CANCELLED }
}
