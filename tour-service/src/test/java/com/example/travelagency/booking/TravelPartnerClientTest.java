package com.example.travelagency.booking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TravelPartnerClientTest {
    private final TravelPartnerClient client = new TravelPartnerClient(0);

    @Test
    void prices_twoPassengers_returnPerPassengerPrices() {
        assertEquals(new BigDecimal("50.00"), client.transferPrice(7L, 2));
        assertEquals(new BigDecimal("30.00"), client.insurancePrice(7L, 2, false));
    }

    @Test
    void confirmation_availableProvider_completesNormally() {
        assertDoesNotThrow(() -> client.sendConfirmation(7L, "traveler@example.com", false));
    }

    @Test
    void confirmation_simulatedFailure_throwsClearError() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.sendConfirmation(7L, "traveler@example.com", true));

        assertEquals("Confirmation provider is unavailable", error.getMessage());
    }

    @Test
    void insurance_simulatedFailure_throwsClearError() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.insurancePrice(7L, 2, true));

        assertEquals("Insurance provider is unavailable", error.getMessage());
    }

    @Test
    void interruptedPartnerCall_restoresInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.transferPrice(7L, 2));

            assertEquals("Partner operation was interrupted", error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
