package com.example.travelagency.tour.validation;

import java.time.LocalDate;

public interface TourDateRange {
    LocalDate startDate();

    LocalDate endDate();
}
