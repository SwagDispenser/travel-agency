package com.example.travelagency.booking.catalog;

/** Composite for adapter wiring; business services depend on the individual ports. */
public interface TourCatalog extends TourPriceLookup, TourAvailabilityLookup, TourItineraryLookup {}
