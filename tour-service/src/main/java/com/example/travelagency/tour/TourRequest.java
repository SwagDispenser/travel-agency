package com.example.travelagency.tour;

import com.example.travelagency.tour.validation.TourDateRange;
import com.example.travelagency.tour.validation.ValidTourDates;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@ValidTourDates
public record TourRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 80) String country,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 500) String description,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull @FutureOrPresent LocalDate endDate,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull @Min(0) Integer availableSeats,
        boolean featured
) implements TourDateRange {
}
