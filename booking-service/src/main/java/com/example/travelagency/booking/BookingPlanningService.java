package com.example.travelagency.booking;

import com.example.travelagency.booking.catalog.*;
import com.example.travelagency.contract.catalog.TourItinerary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BookingPlanningService {
    private final TourPriceLookup prices;
    private final TourAvailabilityLookup availability;
    private final TourItineraryLookup itineraries;

    public BookingPlanningService(TourPriceLookup prices, TourAvailabilityLookup availability,
                                  TourItineraryLookup itineraries) {
        this.prices = prices;
        this.availability = availability;
        this.itineraries = itineraries;
    }

    public PriceEstimate estimate(long tourId, int passengers) {
        var price = prices.price(tourId);
        return new PriceEstimate(tourId, passengers, price.price(),
                price.price().multiply(BigDecimal.valueOf(passengers)));
    }

    public SeatCheck checkSeats(long tourId, int passengers) {
        var seats = availability.availability(tourId);
        return new SeatCheck(tourId, passengers, seats.availableSeats(), seats.availableSeats() >= passengers);
    }

    public TourItinerary itinerary(long tourId) {
        return itineraries.itinerary(tourId);
    }

    public record PriceEstimate(long tourId, int passengers, BigDecimal unitPrice, BigDecimal total) {}
    public record SeatCheck(long tourId, int passengers, int availableSeats, boolean available) {}
}
