package com.example.travelagency.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:booking-http;DB_CLOSE_DELAY=-1", "travel.messaging.enabled=false"})
class BookingHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void invalidRequestReturns400AndCreatesNeitherBookingNorEvent() {
        var response = http.postForEntity("/api/bookings", new BookingRequest("", 0, 0), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_outbox", Integer.class)).isZero();
    }
}
