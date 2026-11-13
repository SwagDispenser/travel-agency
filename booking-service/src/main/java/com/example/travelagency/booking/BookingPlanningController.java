package com.example.travelagency.booking;

import com.example.travelagency.contract.catalog.TourItinerary;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/bookings/planning/{tourId}")
public class BookingPlanningController {
    private final BookingPlanningService planning;

    public BookingPlanningController(BookingPlanningService planning) {
        this.planning = planning;
    }

    @GetMapping("/estimate")
    public BookingPlanningService.PriceEstimate estimate(@PathVariable @Min(1) long tourId,
            @RequestParam(defaultValue = "1") @Min(1) int passengers) {
        return planning.estimate(tourId, passengers);
    }

    @GetMapping("/availability")
    public BookingPlanningService.SeatCheck availability(@PathVariable @Min(1) long tourId,
            @RequestParam(defaultValue = "1") @Min(1) int passengers) {
        return planning.checkSeats(tourId, passengers);
    }

    @GetMapping("/itinerary")
    public TourItinerary itinerary(@PathVariable @Min(1) long tourId) {
        return planning.itinerary(tourId);
    }
}
