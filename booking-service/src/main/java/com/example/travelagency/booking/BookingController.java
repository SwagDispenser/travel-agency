package com.example.travelagency.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<BookingResponse> findAll() {
        return bookingService.findAll();
    }

    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable long id) {
        return bookingService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable long id) {
        return bookingService.cancel(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingRequest request) {
        return bookingService.create(request);
    }
}
