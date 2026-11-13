package com.example.travelagency.catalog;

import com.example.travelagency.contract.catalog.*;
import com.example.travelagency.tour.TourService;
import org.springframework.stereotype.Service;

@Service
public class TourCatalogService {
    private final TourService tours;

    public TourCatalogService(TourService tours) {
        this.tours = tours;
    }

    public TourPrice price(long tourId) {
        var tour = tours.findById(tourId);
        return new TourPrice(tour.id(), tour.price());
    }

    public TourAvailability availability(long tourId) {
        var tour = tours.findById(tourId);
        return new TourAvailability(tour.id(), tour.availableSeats());
    }

    public TourItinerary itinerary(long tourId) {
        var tour = tours.findById(tourId);
        return new TourItinerary(tour.id(), tour.title(), tour.country(), tour.city(),
                tour.startDate(), tour.endDate());
    }
}
