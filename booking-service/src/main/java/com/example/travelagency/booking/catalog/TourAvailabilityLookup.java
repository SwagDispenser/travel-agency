package com.example.travelagency.booking.catalog;

import com.example.travelagency.contract.catalog.TourAvailability;

public interface TourAvailabilityLookup {
    TourAvailability availability(long tourId);
}
