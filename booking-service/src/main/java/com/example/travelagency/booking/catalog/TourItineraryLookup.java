package com.example.travelagency.booking.catalog;

import com.example.travelagency.contract.catalog.TourItinerary;

public interface TourItineraryLookup {
    TourItinerary itinerary(long tourId);
}
