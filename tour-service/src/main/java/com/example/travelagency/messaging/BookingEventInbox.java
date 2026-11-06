package com.example.travelagency.messaging;

import com.example.travelagency.contract.BookingEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BookingEventInbox {
    private final JdbcTemplate jdbc;

    public BookingEventInbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean record(BookingEvent event) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM booking_event_receipts WHERE event_id = ?", Integer.class, event.eventId()) > 0) {
            return false;
        }
        try {
            jdbc.update("""
                    INSERT INTO booking_event_receipts(event_id, event_type, booking_id, customer_name, tour_id, seats, status, occurred_at, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, event.eventId(), event.type().name(), event.bookingId(), event.customerName(), event.tourId(),
                    event.seats(), event.status().name(), event.occurredAt().toString(), Instant.now().toString());
            return true;
        } catch (DuplicateKeyException duplicate) {
            // event_id is the primary key, including when two deliveries race.
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<BookingEventReceipt> findAll() {
        return jdbc.query("SELECT * FROM booking_event_receipts ORDER BY occurred_at, event_id", (row, index) ->
                new BookingEventReceipt(new BookingEvent(row.getString("event_id"),
                        BookingEvent.Type.valueOf(row.getString("event_type")), row.getLong("booking_id"),
                        row.getString("customer_name"), row.getLong("tour_id"), row.getInt("seats"),
                        BookingEvent.Status.valueOf(row.getString("status")), Instant.parse(row.getString("occurred_at"))),
                        Instant.parse(row.getString("received_at"))));
    }
}
