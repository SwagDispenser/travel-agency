package com.example.travelagency.messaging;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class BookingEventController {
    private final BookingEventInbox inbox;

    public BookingEventController(BookingEventInbox inbox) {
        this.inbox = inbox;
    }

    @GetMapping("/api/booking-events")
    public List<BookingEventReceipt> received() {
        return inbox.findAll();
    }
}
