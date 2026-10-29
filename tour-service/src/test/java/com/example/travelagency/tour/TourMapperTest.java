package com.example.travelagency.tour;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TourMapperTest {
    private final TourMapper mapper = new TourMapperImpl();

    @Test
    void toEntity_validRequest_copiesAllFieldsExceptId() {
        TourRequest request = request("Rome", true);

        Tour tour = mapper.toEntity(request);

        assertNull(tour.getId());
        assertEquals("Rome", tour.getTitle());
        assertEquals("Italy", tour.getCountry());
        assertEquals("Rome", tour.getCity());
        assertEquals("Guided city tour", tour.getDescription());
        assertEquals(LocalDate.of(2030, 6, 1), tour.getStartDate());
        assertEquals(LocalDate.of(2030, 6, 5), tour.getEndDate());
        assertEquals(new BigDecimal("450.00"), tour.getPrice());
        assertEquals(5, tour.getAvailableSeats());
        assertTrue(tour.isFeatured());
    }

    @Test
    void toResponse_existingTour_copiesAllFields() {
        Tour tour = mapper.toEntity(request("Rome", true));
        tour.setId(13L);

        TourResponse result = mapper.toResponse(tour);

        assertEquals(13L, result.id());
        assertEquals("Rome", result.title());
        assertEquals("Italy", result.country());
        assertEquals("Rome", result.city());
        assertEquals("Guided city tour", result.description());
        assertEquals(LocalDate.of(2030, 6, 1), result.startDate());
        assertEquals(LocalDate.of(2030, 6, 5), result.endDate());
        assertEquals(new BigDecimal("450.00"), result.price());
        assertEquals(5, result.availableSeats());
        assertTrue(result.featured());
    }

    @Test
    void updateEntity_validRequest_replacesFieldsButKeepsId() {
        Tour tour = mapper.toEntity(request("Rome", true));
        tour.setId(13L);

        mapper.updateEntity(request("Milan", false), tour);

        assertEquals(13L, tour.getId());
        assertEquals("Milan", tour.getTitle());
        assertFalse(tour.isFeatured());
        assertEquals("Italy", tour.getCountry());
        assertEquals(new BigDecimal("450.00"), tour.getPrice());
    }

    @Test
    void mapping_nullInput_returnsNullOrLeavesEntityUnchanged() {
        Tour tour = mapper.toEntity(request("Rome", true));

        mapper.updateEntity(null, tour);

        assertEquals("Rome", tour.getTitle());
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toResponse(null));
    }

    private static TourRequest request(String title, boolean featured) {
        return new TourRequest(title, "Italy", "Rome", "Guided city tour",
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5),
                new BigDecimal("450.00"), 5, featured);
    }
}
