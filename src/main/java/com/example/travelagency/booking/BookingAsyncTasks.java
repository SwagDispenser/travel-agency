package com.example.travelagency.booking;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Service
public class BookingAsyncTasks {
    private final TravelPartnerClient client;

    public BookingAsyncTasks(TravelPartnerClient client) {
        this.client = client;
    }

    @Async("travelExecutor")
    public void sendConfirmation(Long tourId, String email, boolean simulateFailure) {
        client.sendConfirmation(tourId, email, simulateFailure);
    }

    @Async("travelExecutor")
    public CompletableFuture<BigDecimal> transferPrice(Long tourId, int passengers) {
        return CompletableFuture.completedFuture(client.transferPrice(tourId, passengers));
    }

    @Async("travelExecutor")
    public CompletableFuture<BigDecimal> insurancePrice(Long tourId, int passengers, boolean simulateFailure) {
        return CompletableFuture.completedFuture(client.insurancePrice(tourId, passengers, simulateFailure));
    }
}
