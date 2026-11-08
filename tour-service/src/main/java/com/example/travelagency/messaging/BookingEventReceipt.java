package com.example.travelagency.messaging;

import com.example.travelagency.contract.BookingEvent;
import java.time.Instant;

public record BookingEventReceipt(BookingEvent event, Instant receivedAt) {
}
