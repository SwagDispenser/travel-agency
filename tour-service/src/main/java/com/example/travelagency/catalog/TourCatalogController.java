package com.example.travelagency.catalog;

import com.example.travelagency.contract.catalog.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/tours")
public class TourCatalogController {
    private final TourCatalogService catalog;

    public TourCatalogController(TourCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/{id}/price")
    public TourPrice price(@PathVariable long id) {
        return catalog.price(id);
    }

    @GetMapping("/{id}/availability")
    public TourAvailability availability(@PathVariable long id) {
        return catalog.availability(id);
    }

    @GetMapping("/{id}/itinerary")
    public TourItinerary itinerary(@PathVariable long id) {
        return catalog.itinerary(id);
    }
}
