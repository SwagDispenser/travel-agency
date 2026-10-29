package com.example.travelagency.tour;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TourServiceTest {
    @Mock TourRepository repository;
    @Mock TourMapper mapper;
    @InjectMocks TourService service;

    @Test
    void findAll_withTours_returnsMappedResponses() {
        Tour first = tour(1L, false);
        Tour second = tour(2L, true);
        TourResponse one = response(1L, false);
        TourResponse two = response(2L, true);
        when(repository.findAll()).thenReturn(List.of(first, second));
        when(mapper.toResponse(first)).thenReturn(one);
        when(mapper.toResponse(second)).thenReturn(two);

        List<TourResponse> result = service.findAll();

        assertEquals(List.of(one, two), result);
        verify(mapper).toResponse(first);
        verify(mapper).toResponse(second);
    }

    @Test
    void findAll_emptyRepository_returnsEmptyList() {
        when(repository.findAll()).thenReturn(List.of());

        List<TourResponse> result = service.findAll();

        assertTrue(result.isEmpty());
        verifyNoInteractions(mapper);
    }

    @Test
    void findById_existingTour_returnsMappedResponse() {
        Tour tour = tour(7L, false);
        TourResponse expected = response(7L, false);
        when(repository.findById(7L)).thenReturn(Optional.of(tour));
        when(mapper.toResponse(tour)).thenReturn(expected);

        TourResponse result = service.findById(7L);

        assertSame(expected, result);
        verify(repository).findById(7L);
    }

    @Test
    void findById_missingTour_throwsNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        TourNotFoundException error = assertThrows(TourNotFoundException.class,
                () -> service.findById(7L));

        assertEquals("Tour with id 7 was not found", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void create_validRequest_savesMappedTourAndReturnsResponse() {
        TourRequest request = request(false);
        Tour entity = tour(null, false);
        Tour saved = tour(8L, false);
        TourResponse expected = response(8L, false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(expected);

        TourResponse result = service.create(request);

        assertSame(expected, result);
        ArgumentCaptor<Tour> captured = ArgumentCaptor.forClass(Tour.class);
        verify(repository).save(captured.capture());
        assertSame(entity, captured.getValue());
        verify(mapper).toEntity(request);
    }

    @Test
    void create_repositoryRejectsSave_propagatesFailure() {
        TourRequest request = request(false);
        Tour entity = tour(null, false);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(entity)).thenThrow(new IllegalStateException("save failed"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.create(request));

        assertEquals("save failed", error.getMessage());
        verify(repository).save(entity);
        verify(mapper, never()).toResponse(any());
    }

    @Test
    void update_existingTour_changesEntityAndReturnsResponse() {
        TourRequest request = request(true);
        Tour entity = tour(9L, false);
        TourResponse expected = response(9L, true);
        when(repository.findById(9L)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);

        TourResponse result = service.update(9L, request);

        assertSame(expected, result);
        verify(mapper).updateEntity(request, entity);
        verify(repository, never()).save(any());
    }

    @Test
    void update_missingTour_throwsWithoutChangingEntity() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        TourNotFoundException error = assertThrows(TourNotFoundException.class,
                () -> service.update(9L, request(true)));

        assertEquals("Tour with id 9 was not found", error.getMessage());
        verifyNoInteractions(mapper);
        verify(repository, never()).save(any());
    }

    @Test
    void delete_existingTour_deletesExactlyThatEntity() {
        Tour entity = tour(10L, false);
        when(repository.findById(10L)).thenReturn(Optional.of(entity));

        service.delete(10L);

        ArgumentCaptor<Tour> captured = ArgumentCaptor.forClass(Tour.class);
        verify(repository).delete(captured.capture());
        assertSame(entity, captured.getValue());
    }

    @Test
    void delete_missingTour_throwsWithoutDeleting() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        TourNotFoundException error = assertThrows(TourNotFoundException.class,
                () -> service.delete(10L));

        assertEquals("Tour with id 10 was not found", error.getMessage());
        verify(repository, never()).delete(any());
    }

    @Test
    void toggleFeatured_notFeatured_setsTrueAndReturnsResponse() {
        Tour entity = tour(11L, false);
        TourResponse expected = response(11L, true);
        when(repository.findById(11L)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expected);

        TourResponse result = service.toggleFeatured(11L);

        assertTrue(entity.isFeatured());
        assertSame(expected, result);
        verify(mapper).toResponse(entity);
    }

    @Test
    void toggleFeatured_alreadyFeatured_setsFalse() {
        Tour entity = tour(11L, true);
        when(repository.findById(11L)).thenReturn(Optional.of(entity));

        service.toggleFeatured(11L);

        assertFalse(entity.isFeatured());
        verify(mapper).toResponse(entity);
    }

    @Test
    void toggleFeatured_missingTour_throwsWithoutMapping() {
        when(repository.findById(11L)).thenReturn(Optional.empty());

        TourNotFoundException error = assertThrows(TourNotFoundException.class,
                () -> service.toggleFeatured(11L));

        assertEquals("Tour with id 11 was not found", error.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void quote_withoutProxy_calculatesSubtotalWithoutAnAspectFee() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour(7L, false)));

        TourQuote result = service.quote(7L, 2);

        assertEquals(new BigDecimal("440.00"), result.subtotal());
        assertEquals(new BigDecimal("0.00"), result.bookingFee());
        assertEquals(new BigDecimal("440.00"), result.total());
    }

    @Test
    void quote_missingTour_throwsNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(TourNotFoundException.class, () -> service.quote(7L, 2));

        verify(repository).findById(7L);
    }

    @Test
    void quote_notEnoughSeats_throwsClearError() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour(7L, false)));

        InvalidPassengerCountException error = assertThrows(InvalidPassengerCountException.class,
                () -> service.quote(7L, 13));

        assertEquals("Not enough available seats for this tour", error.getMessage());
    }

    @Test
    void quoteViaSelfInvocation_existingTour_returnsBaseQuote() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour(7L, false)));

        TourQuote result = service.quoteViaSelfInvocation(7L, 2);

        assertEquals(new BigDecimal("440.00"), result.total());
    }

    @Test
    void quoteViaSelfInvocation_missingTour_throwsNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(TourNotFoundException.class, () -> service.quoteViaSelfInvocation(7L, 2));

        verify(repository).findById(7L);
    }

    private static Tour tour(Long id, boolean featured) {
        Tour tour = new Tour();
        tour.setId(id);
        tour.setTitle("Krakow");
        tour.setCountry("Poland");
        tour.setCity("Krakow");
        tour.setDescription("City tour");
        tour.setStartDate(LocalDate.of(2030, 5, 1));
        tour.setEndDate(LocalDate.of(2030, 5, 3));
        tour.setPrice(new BigDecimal("220.00"));
        tour.setAvailableSeats(12);
        tour.setFeatured(featured);
        return tour;
    }

    private static TourRequest request(boolean featured) {
        return new TourRequest("Krakow", "Poland", "Krakow", "City tour",
                LocalDate.of(2030, 5, 1), LocalDate.of(2030, 5, 3),
                new BigDecimal("220.00"), 12, featured);
    }

    private static TourResponse response(Long id, boolean featured) {
        return new TourResponse(id, "Krakow", "Poland", "Krakow", "City tour",
                LocalDate.of(2030, 5, 1), LocalDate.of(2030, 5, 3),
                new BigDecimal("220.00"), 12, featured);
    }
}
