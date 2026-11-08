package com.example.travelagency.booking;

import com.example.travelagency.contract.BookingEvent.Status;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Repository
public class BookingRepository {
    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BookingResponse create(BookingRequest request) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO bookings(customer_name, tour_id, seats, status) VALUES (?, ?, ?, 'NEW')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.customerName());
            statement.setLong(2, request.tourId());
            statement.setInt(3, request.seats());
            return statement;
        }, key);
        return new BookingResponse(key.getKey().longValue(), request.customerName(), request.tourId(), request.seats(), Status.NEW);
    }

    public List<BookingResponse> findAll() {
        return jdbc.query("SELECT * FROM bookings ORDER BY id", BookingRepository::map);
    }

    public BookingResponse lock(long id) {
        return jdbc.query("SELECT * FROM bookings WHERE id = ? FOR UPDATE", BookingRepository::map, id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    public BookingResponse changeStatus(BookingResponse booking, Status status) {
        jdbc.update("UPDATE bookings SET status = ? WHERE id = ?", status.name(), booking.id());
        return new BookingResponse(booking.id(), booking.customerName(), booking.tourId(), booking.seats(), status);
    }

    private static BookingResponse map(ResultSet row, int index) throws SQLException {
        return new BookingResponse(row.getLong("id"), row.getString("customer_name"), row.getLong("tour_id"),
                row.getInt("seats"), Status.valueOf(row.getString("status")));
    }
}
