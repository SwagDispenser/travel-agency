package com.example.travelagency.booking;

import com.example.travelagency.tour.InvalidPassengerCountException;
import com.example.travelagency.tour.TourNotFoundException;
import com.example.travelagency.tour.TourResponse;
import com.example.travelagency.tour.TourService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingSupportServiceTest {
    @Mock TourService tours;
    @Mock BookingAsyncTasks tasks;
    BookingSupportService service;

    @BeforeEach
    void createService() {
        service = new BookingSupportService(tours, tasks, 50);
    }

    @Test
    void extras_neverCompletingPartners_returnsFallbackAfterTimeout() throws Exception {
        when(tours.findById(7L)).thenReturn(BookingAsyncExecutionTest.tour());
        when(tasks.transferPrice(7L, 2)).thenReturn(new CompletableFuture<>());
        when(tasks.insurancePrice(7L, 2, false)).thenReturn(new CompletableFuture<>());

        ExtrasQuote result = service.extras(7L, 2, false).get(2, TimeUnit.SECONDS);

        assertFalse(result.available());
        assertNull(result.transferPrice());
        assertNull(result.insurancePrice());
        assertNull(result.total());
        assertEquals("Additional services temporarily unavailable. Please try again later.", result.message());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    void extras_invalidPassengerCount_rejectsBeforeSubmittingAnything(int passengers) {
        assertThrows(InvalidPassengerCountException.class, () -> service.extras(7L, passengers, false));

        verifyNoInteractions(tours, tasks);
    }

    @Test
    void extras_notEnoughSeats_rejectsBeforeSubmittingAnything() {
        TourResponse tour = BookingAsyncExecutionTest.tour();
        when(tours.findById(7L)).thenReturn(new TourResponse(tour.id(), tour.title(), tour.country(), tour.city(),
                tour.description(), tour.startDate(), tour.endDate(), tour.price(), 1, tour.featured()));

        assertThrows(InvalidPassengerCountException.class, () -> service.extras(7L, 2, false));

        verifyNoInteractions(tasks);
    }

    @Test
    void extras_secondSubmissionRejected_cancelsFirstFutureAndPropagatesRejection() {
        when(tours.findById(7L)).thenReturn(BookingAsyncExecutionTest.tour());
        CompletableFuture<BigDecimal> transfer = new CompletableFuture<>();
        TaskRejectedException rejected = new TaskRejectedException("queue full");
        when(tasks.transferPrice(7L, 2)).thenReturn(transfer);
        when(tasks.insurancePrice(7L, 2, false)).thenThrow(rejected);

        TaskRejectedException result = assertThrows(TaskRejectedException.class,
                () -> service.extras(7L, 2, false));

        assertSame(rejected, result);
        assertTrue(transfer.isCancelled());
    }

    @Test
    void confirmation_missingTour_doesNotSubmitAnEmail() {
        when(tours.findById(7L)).thenThrow(new TourNotFoundException(7L));

        assertThrows(TourNotFoundException.class,
                () -> service.queueConfirmation(7L, new ConfirmationRequest("traveler@example.com", false)));

        verifyNoInteractions(tasks);
    }
}
