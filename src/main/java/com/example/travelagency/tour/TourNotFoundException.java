package com.example.travelagency.tour;

public class TourNotFoundException extends RuntimeException {

    public TourNotFoundException(Long id) {
        super("Tour with id " + id + " was not found");
    }
}
