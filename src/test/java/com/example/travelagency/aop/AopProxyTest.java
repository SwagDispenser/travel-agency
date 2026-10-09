package com.example.travelagency.aop;

import com.example.travelagency.tour.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AopProxyTest {
    @Mock TourRepository repository;
    private TourService proxy;

    @BeforeEach
    void createProxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new TourService(repository, new TourMapperImpl()));
        factory.setProxyTargetClass(true);
        factory.addAspect(new PassengerLimitAspect());
        factory.addAspect(new NormalizeTourInputAspect());
        factory.addAspect(new BookingFeeAspect());
        proxy = factory.getProxy();
    }

    @Test
    void quote_externalCall_appliesFee() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour()));

        TourQuote result = proxy.quote(7L, 2);

        assertEquals(new BigDecimal("20.00"), result.bookingFee());
        assertEquals(new BigDecimal("460.00"), result.total());
    }

    @Test
    void quoteViaSelfInvocation_sameQuoteMethod_skipsFeeWithoutAnError() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour()));

        TourQuote result = proxy.quoteViaSelfInvocation(7L, 2);

        assertEquals(new BigDecimal("0.00"), result.bookingFee());
        assertEquals(new BigDecimal("440.00"), result.total());
    }

    @Test
    void quote_externalInvalidPassengerCount_blocksBeforeRepositoryAccess() {
        assertThrows(InvalidPassengerCountException.class, () -> proxy.quote(7L, 0));

        verifyNoInteractions(repository);
    }

    @Test
    void quoteViaSelfInvocation_zeroPassengers_skipsPassengerLimitAdvice() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour()));

        TourQuote result = proxy.quoteViaSelfInvocation(7L, 0);

        assertEquals(0, result.passengers());
        assertEquals(new BigDecimal("0.00"), result.total());
        verify(repository).findById(7L);
    }

    @Test
    void create_externalCall_normalizesInputBeforeSaving() {
        TourRequest request = request();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TourResponse result = proxy.create(request);

        assertEquals("Rome", result.title());
        assertEquals("Italy", result.country());
        assertEquals("Rome", result.city());
        assertEquals("City tour", result.description());
        ArgumentCaptor<Tour> saved = ArgumentCaptor.forClass(Tour.class);
        verify(repository).save(saved.capture());
        assertEquals("Rome", saved.getValue().getTitle());
        assertEquals("  Rome  ", request.title());
    }

    @Test
    void update_externalCall_normalizesInputBeforeMapping() {
        Tour entity = tour();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        TourResponse result = proxy.update(7L, request());

        assertEquals("Rome", result.title());
        assertEquals("City tour", entity.getDescription());
    }

    private static Tour tour() {
        Tour tour = new Tour();
        tour.setId(7L);
        tour.setPrice(new BigDecimal("220.00"));
        tour.setAvailableSeats(12);
        return tour;
    }

    private static TourRequest request() {
        return new TourRequest("  Rome  ", " Italy ", " Rome ", " City tour ",
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5), new BigDecimal("220.00"), 5, false);
    }
}
