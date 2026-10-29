package com.example.travelagency.booking;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BookingServiceTest {
    private final BookingService service = new BookingService();

    @Test
    void createsAndListsBooking() {
        BookingResponse booking = service.create(new BookingRequest("Dmytro", 1, 2));
        assertThat(booking.id()).isEqualTo(1);
        assertThat(service.findAll()).containsExactly(booking);
    }
}
