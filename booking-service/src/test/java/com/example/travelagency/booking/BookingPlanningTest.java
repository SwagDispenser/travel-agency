package com.example.travelagency.booking;

import com.example.travelagency.booking.catalog.*;
import com.example.travelagency.contract.catalog.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BookingPlanningTest {
    TourPriceLookup prices = mock(TourPriceLookup.class);
    TourAvailabilityLookup seats = mock(TourAvailabilityLookup.class);
    TourItineraryLookup itineraries = mock(TourItineraryLookup.class);
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = standaloneSetup(new BookingPlanningController(new BookingPlanningService(prices, seats, itineraries)))
                .setControllerAdvice(new TourLookupExceptionHandler()).build();
    }

    @Test
    void threeBusinessActionsUseTheirPorts() throws Exception {
        when(prices.price(7)).thenReturn(new TourPrice(7, new BigDecimal("123.45")));
        when(seats.availability(7)).thenReturn(new TourAvailability(7, 2));
        when(itineraries.itinerary(7)).thenReturn(new TourItinerary(7, "Kyiv", "Ukraine", "Kyiv",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5)));
        mvc.perform(get("/api/bookings/planning/7/estimate?passengers=3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(370.35));
        mvc.perform(get("/api/bookings/planning/7/availability?passengers=3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/bookings/planning/7/availability?passengers=2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/bookings/planning/7/itinerary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.city").value("Kyiv"));
        verify(prices).price(7);
        verify(seats, times(2)).availability(7);
        verify(itineraries).itinerary(7);
    }

    @Test
    void ownExceptionsProduce404And503ForEveryAction() throws Exception {
        when(prices.price(7)).thenThrow(new TourLookupNotFoundException(7, null));
        when(seats.availability(7)).thenThrow(new TourLookupNotFoundException(7, null));
        when(itineraries.itinerary(7)).thenThrow(new TourLookupNotFoundException(7, null));
        for (String action : new String[]{"estimate", "availability", "itinerary"}) {
            mvc.perform(get("/api/bookings/planning/7/" + action)).andExpect(status().isNotFound());
        }
        reset(prices, seats, itineraries);
        when(prices.price(7)).thenThrow(new TourLookupUnavailableException(null));
        when(seats.availability(7)).thenThrow(new TourLookupUnavailableException(null));
        when(itineraries.itinerary(7)).thenThrow(new TourLookupUnavailableException(null));
        for (String action : new String[]{"estimate", "availability", "itinerary"}) {
            mvc.perform(get("/api/bookings/planning/7/" + action)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.detail").value("Tour service is unavailable. Please try again later."));
        }
    }
}
