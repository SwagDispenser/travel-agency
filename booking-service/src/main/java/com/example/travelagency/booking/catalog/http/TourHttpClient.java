package com.example.travelagency.booking.catalog.http;

import com.example.travelagency.contract.catalog.*;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/api/catalog/tours", accept = "application/json")
public interface TourHttpClient {
    @GetExchange("/{id}/price")
    TourPrice price(@PathVariable("id") long id);

    @GetExchange("/{id}/availability")
    TourAvailability availability(@PathVariable("id") long id);

    @GetExchange("/{id}/itinerary")
    TourItinerary itinerary(@PathVariable("id") long id);
}
