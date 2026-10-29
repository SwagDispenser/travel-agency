package com.example.travelagency.tour;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourRestControllerTest {
    @Mock TourService service;
    @InjectMocks TourRestController controller;

    @Test
    void findAll_andFindById_returnServiceResults() {
        TourResponse tour = response();
        when(service.findAll()).thenReturn(List.of(tour));
        when(service.findById(3L)).thenReturn(tour);

        List<TourResponse> all = controller.findAll();
        TourResponse one = controller.findById(3L);

        assertEquals(List.of(tour), all);
        assertSame(tour, one);
        verify(service).findById(3L);
    }

    @Test
    void create_andUpdate_forwardRequestAndReturnServiceResults() {
        TourRequest request = request();
        TourResponse tour = response();
        when(service.create(request)).thenReturn(tour);
        when(service.update(3L, request)).thenReturn(tour);

        TourResponse created = controller.create(request);
        TourResponse updated = controller.update(3L, request);

        assertSame(tour, created);
        assertSame(tour, updated);
        verify(service).create(request);
        verify(service).update(3L, request);
    }

    @Test
    void delete_forwardsSelectedIdToService() {
        controller.delete(3L);

        verify(service).delete(3L);
    }

    @Test
    void toggleFeatured_returnsUpdatedTourFromService() {
        TourResponse tour = response();
        when(service.toggleFeatured(3L)).thenReturn(tour);

        TourResponse result = controller.toggleFeatured(3L);

        assertSame(tour, result);
        verify(service).toggleFeatured(3L);
    }

    private static TourRequest request() {
        return new TourRequest("Rome", "Italy", "Rome", "Guided tour",
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5),
                new BigDecimal("450.00"), 5, false);
    }

    private static TourResponse response() {
        return new TourResponse(3L, "Rome", "Italy", "Rome", "Guided tour",
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5),
                new BigDecimal("450.00"), 5, false);
    }
}
