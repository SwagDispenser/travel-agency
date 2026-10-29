package com.example.travelagency.tour;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TourResponse(
        Long id,
        String title,
        String country,
        String city,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal price,
        Integer availableSeats,
        boolean featured
) {
}
