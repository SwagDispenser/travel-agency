package com.example.travelagency.booking;

import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BookingService {
    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, BookingResponse> bookings = new ConcurrentHashMap<>();

    public BookingResponse create(BookingRequest request) {
        long id = nextId.getAndIncrement();
        BookingResponse booking = new BookingResponse(id, request.customerName(), request.tourId(), request.seats());
        bookings.put(id, booking);
        return booking;
    }

    public List<BookingResponse> findAll() {
        return bookings.values().stream().sorted(Comparator.comparingLong(BookingResponse::id)).toList();
    }
}
