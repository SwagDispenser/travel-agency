package com.example.travelagency.booking;

import com.example.travelagency.tour.InvalidPassengerCountException;
import com.example.travelagency.tour.TourResponse;
import com.example.travelagency.tour.TourService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class BookingSupportService {
    private static final Logger log = LoggerFactory.getLogger(BookingSupportService.class);
    private final TourService tourService;
    private final BookingAsyncTasks tasks;
    private final long timeoutMs;

    public BookingSupportService(TourService tourService, BookingAsyncTasks tasks,
                                 @Value("${travel.async.timeout-ms:2500}") long timeoutMs) {
        this.tourService = tourService;
        this.tasks = tasks;
        this.timeoutMs = timeoutMs;
    }

    public void queueConfirmation(Long tourId, ConfirmationRequest request) {
        tourService.findById(tourId);
        tasks.sendConfirmation(tourId, request.email(), request.simulateFailure());
    }

    public CompletableFuture<ExtrasQuote> extras(Long tourId, int passengers, boolean simulateInsuranceFailure) {
        if (passengers < 1 || passengers > 10) {
            throw new InvalidPassengerCountException("Passenger count must be between 1 and 10");
        }
        TourResponse tour = tourService.findById(tourId);
        if (passengers > tour.availableSeats()) {
            throw new InvalidPassengerCountException("Not enough available seats for this tour");
        }

        CompletableFuture<BigDecimal> transfer = tasks.transferPrice(tourId, passengers);
        CompletableFuture<BigDecimal> insurance;
        try {
            insurance = tasks.insurancePrice(tourId, passengers, simulateInsuranceFailure);
        } catch (TaskRejectedException exception) {
            transfer.cancel(false);
            throw exception;
        }

        return transfer.thenCombine(insurance, (transferPrice, insurancePrice) ->
                        new ExtrasQuote(tourId, passengers, transferPrice, insurancePrice,
                                transferPrice.add(insurancePrice), true, "Prices are available"))
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    log.warn("Extra prices for tour {} are unavailable", tourId, exception);
                    return new ExtrasQuote(tourId, passengers, null, null, null, false,
                            "Additional services temporarily unavailable. Please try again later.");
                });
    }
}
