package com.example.travelagency.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TravelPartnerClient {
    private static final Logger log = LoggerFactory.getLogger(TravelPartnerClient.class);
    private final long delayMs;

    public TravelPartnerClient(@Value("${travel.async.partner-delay-ms:1000}") long delayMs) {
        this.delayMs = delayMs;
    }

    public void sendConfirmation(Long tourId, String email, boolean simulateFailure) {
        pause("confirmation", tourId);
        if (simulateFailure) {
            throw new IllegalStateException("Confirmation provider is unavailable");
        }
        log.info("Demo confirmation for tour {} to {} prepared for delivery on {}", tourId, email,
                Thread.currentThread().getName());
    }

    public BigDecimal transferPrice(Long tourId, int passengers) {
        pause("transfer", tourId);
        return new BigDecimal("25.00").multiply(BigDecimal.valueOf(passengers));
    }

    public BigDecimal insurancePrice(Long tourId, int passengers, boolean simulateFailure) {
        pause("insurance", tourId);
        if (simulateFailure) {
            throw new IllegalStateException("Insurance provider is unavailable");
        }
        return new BigDecimal("15.00").multiply(BigDecimal.valueOf(passengers));
    }

    private void pause(String operation, Long tourId) {
        log.info("Starting {} for tour {} on {}", operation, tourId, Thread.currentThread().getName());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Partner operation was interrupted", exception);
        }
        log.info("Finished waiting for {} for tour {} on {}", operation, tourId,
                Thread.currentThread().getName());
    }
}
