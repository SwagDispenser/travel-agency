package com.example.travelagency.booking.messaging;

import com.example.travelagency.contract.BookingEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(BookingEvent event) {
        jdbc.update("""
                INSERT INTO booking_outbox(event_id, event_type, booking_id, customer_name, tour_id, seats, status, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, event.eventId(), event.type().name(), event.bookingId(), event.customerName(), event.tourId(),
                event.seats(), event.status().name(), event.occurredAt().toString());
    }

    public List<BookingEvent> pending() {
        return jdbc.query("SELECT * FROM booking_outbox WHERE published = FALSE ORDER BY id LIMIT 50", (row, index) ->
                new BookingEvent(row.getString("event_id"), BookingEvent.Type.valueOf(row.getString("event_type")),
                        row.getLong("booking_id"), row.getString("customer_name"), row.getLong("tour_id"), row.getInt("seats"),
                        BookingEvent.Status.valueOf(row.getString("status")), Instant.parse(row.getString("occurred_at"))));
    }

    public void markPublished(String eventId) {
        jdbc.update("UPDATE booking_outbox SET published = TRUE WHERE event_id = ?", eventId);
    }
}
