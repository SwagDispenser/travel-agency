package com.example.travelagency.web;

import com.example.travelagency.tour.TourRequest;
import com.example.travelagency.tour.TourResponse;
import com.example.travelagency.tour.TourService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TourPageControllerTest {
    @Mock TourService service;
    @Mock BindingResult errors;
    @InjectMocks TourPageController controller;

    @Test
    void list_withTours_addsToursToModel() {
        ExtendedModelMap model = new ExtendedModelMap();
        List<TourResponse> tours = List.of(response());
        when(service.findAll()).thenReturn(tours);

        String view = controller.list(model);

        assertEquals("tours/list", view);
        assertSame(tours, model.get("tours"));
    }

    @Test
    void createForm_newTour_addsEmptyForm() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.createForm(model);

        assertEquals("tours/form", view);
        assertEquals("create", model.get("mode"));
        assertInstanceOf(TourForm.class, model.get("tour"));
    }

    @Test
    void create_validForm_callsServiceWithAllFields() {
        TourForm form = form();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.create(form, errors, model);

        assertEquals("redirect:/tours", view);
        ArgumentCaptor<TourRequest> captured = ArgumentCaptor.forClass(TourRequest.class);
        verify(service).create(captured.capture());
        assertRequestMatchesForm(captured.getValue());
    }

    @Test
    void create_invalidForm_showsFormWithoutCallingService() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(errors.hasErrors()).thenReturn(true);

        String view = controller.create(form(), errors, model);

        assertEquals("tours/form", view);
        assertEquals("create", model.get("mode"));
        verifyNoInteractions(service);
    }

    @Test
    void editForm_existingTour_populatesAllFields() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(service.findById(4L)).thenReturn(response());

        String view = controller.editForm(4L, model);

        assertEquals("tours/form", view);
        assertEquals("edit", model.get("mode"));
        assertEquals(4L, model.get("tourId"));
        TourForm form = assertInstanceOf(TourForm.class, model.get("tour"));
        assertEquals("Rome", form.getTitle());
        assertEquals("Italy", form.getCountry());
        assertEquals("Rome", form.getCity());
        assertEquals("Guided tour", form.getDescription());
        assertEquals(LocalDate.of(2030, 6, 1), form.getStartDate());
        assertEquals(LocalDate.of(2030, 6, 5), form.getEndDate());
        assertEquals(new BigDecimal("450.00"), form.getPrice());
        assertEquals(5, form.getAvailableSeats());
        assertTrue(form.isFeatured());
    }

    @Test
    void update_validForm_callsServiceForSelectedTour() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.update(4L, form(), errors, model);

        assertEquals("redirect:/tours", view);
        ArgumentCaptor<TourRequest> captured = ArgumentCaptor.forClass(TourRequest.class);
        verify(service).update(eq(4L), captured.capture());
        assertRequestMatchesForm(captured.getValue());
    }

    @Test
    void update_invalidForm_showsEditFormWithoutCallingService() {
        ExtendedModelMap model = new ExtendedModelMap();
        when(errors.hasErrors()).thenReturn(true);

        String view = controller.update(4L, form(), errors, model);

        assertEquals("tours/form", view);
        assertEquals("edit", model.get("mode"));
        assertEquals(4L, model.get("tourId"));
        verifyNoInteractions(service);
    }

    @Test
    void delete_existingTour_callsServiceAndRedirects() {
        String view = controller.delete(4L);

        assertEquals("redirect:/tours", view);
        verify(service).delete(4L);
    }

    @Test
    void toggleFeatured_existingTour_callsServiceAndRedirects() {
        String view = controller.toggleFeatured(4L);

        assertEquals("redirect:/tours", view);
        verify(service).toggleFeatured(4L);
    }

    private static TourForm form() {
        TourForm form = new TourForm();
        form.setTitle("Rome");
        form.setCountry("Italy");
        form.setCity("Rome");
        form.setDescription("Guided tour");
        form.setStartDate(LocalDate.of(2030, 6, 1));
        form.setEndDate(LocalDate.of(2030, 6, 5));
        form.setPrice(new BigDecimal("450.00"));
        form.setAvailableSeats(5);
        form.setFeatured(true);
        return form;
    }

    private static TourResponse response() {
        return new TourResponse(4L, "Rome", "Italy", "Rome", "Guided tour",
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5),
                new BigDecimal("450.00"), 5, true);
    }

    private static void assertRequestMatchesForm(TourRequest request) {
        assertEquals("Rome", request.title());
        assertEquals("Italy", request.country());
        assertEquals("Rome", request.city());
        assertEquals("Guided tour", request.description());
        assertEquals(LocalDate.of(2030, 6, 1), request.startDate());
        assertEquals(LocalDate.of(2030, 6, 5), request.endDate());
        assertEquals(new BigDecimal("450.00"), request.price());
        assertEquals(5, request.availableSeats());
        assertTrue(request.featured());
    }
}
