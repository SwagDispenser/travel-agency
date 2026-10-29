package com.example.travelagency.web;

import com.example.travelagency.booking.BookingSupportService;
import com.example.travelagency.booking.ConfirmationRequest;
import com.example.travelagency.booking.ExtrasQuote;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/tours/{id}/booking-support")
public class BookingSupportController {
    private final BookingSupportService service;

    public BookingSupportController(BookingSupportService service) {
        this.service = service;
    }

    @PostMapping("/confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> confirmation(@PathVariable Long id, @Valid @RequestBody ConfirmationRequest request) {
        service.queueConfirmation(id, request);
        return Map.of("status", "QUEUED", "message", "Confirmation accepted for background processing");
    }

    @GetMapping("/extras")
    public CompletableFuture<ExtrasQuote> extras(@PathVariable Long id,
                                                @RequestParam(defaultValue = "1") int passengers,
                                                @RequestParam(defaultValue = "false") boolean simulateInsuranceFailure) {
        return service.extras(id, passengers, simulateInsuranceFailure);
    }
}
