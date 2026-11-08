package com.example.travelagency.booking;

import com.example.travelagency.booking.messaging.OutboxRepository;
import com.example.travelagency.contract.BookingEvent;
import com.example.travelagency.contract.BookingEvent.Status;
import com.example.travelagency.contract.BookingEvent.Type;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final OutboxRepository outbox;

    public BookingService(BookingRepository bookings, OutboxRepository outbox) {
        this.bookings = bookings;
        this.outbox = outbox;
    }

    @Transactional
    public BookingResponse create(BookingRequest request) {
        BookingResponse booking = bookings.create(request);
        enqueue(booking, Type.CREATED);
        return booking;
    }

    @Transactional
    public BookingResponse confirm(long id) {
        BookingResponse booking = bookings.lock(id);
        if (booking.status() != Status.NEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a NEW booking can be confirmed");
        }
        BookingResponse confirmed = bookings.changeStatus(booking, Status.CONFIRMED);
        enqueue(confirmed, Type.CONFIRMED);
        return confirmed;
    }

    @Transactional
    public BookingResponse cancel(long id) {
        BookingResponse booking = bookings.lock(id);
        if (booking.status() == Status.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is already cancelled");
        }
        BookingResponse cancelled = bookings.changeStatus(booking, Status.CANCELLED);
        enqueue(cancelled, Type.CANCELLED);
        return cancelled;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findAll() {
        return bookings.findAll();
    }

    private void enqueue(BookingResponse booking, Type type) {
        // The business row and outbox row commit or roll back together. No network call here.
        outbox.add(new BookingEvent(UUID.randomUUID().toString(), type, booking.id(), booking.customerName(),
                booking.tourId(), booking.seats(), booking.status(), Instant.now()));
    }
}
