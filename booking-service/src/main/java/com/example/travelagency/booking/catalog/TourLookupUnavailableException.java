package com.example.travelagency.booking.catalog;

public class TourLookupUnavailableException extends RuntimeException {
    public TourLookupUnavailableException(Throwable cause) {
        super("Tour service is unavailable. Please try again later.", cause);
    }
}
