package com.example.travelagency.booking.catalog;

public class TourLookupNotFoundException extends RuntimeException {
    public TourLookupNotFoundException(long tourId, Throwable cause) {
        super("Tour " + tourId + " not found", cause);
    }
}
