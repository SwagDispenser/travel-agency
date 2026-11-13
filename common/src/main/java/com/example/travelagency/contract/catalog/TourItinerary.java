package com.example.travelagency.contract.catalog;

import java.time.LocalDate;

public record TourItinerary(long tourId, String title, String country, String city,
                            LocalDate startDate, LocalDate endDate) {}
