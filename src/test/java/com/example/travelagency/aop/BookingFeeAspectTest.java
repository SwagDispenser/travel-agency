package com.example.travelagency.aop;

import com.example.travelagency.tour.TourQuote;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingFeeAspectTest {
    @Mock ProceedingJoinPoint joinPoint;
    private final BookingFeeAspect aspect = new BookingFeeAspect();

    @Test
    void addFee_quoteReturned_addsSingleFeeToTotal() throws Throwable {
        TourQuote original = quote("0.00");
        when(joinPoint.proceed()).thenReturn(original);

        TourQuote result = (TourQuote) aspect.addFee(joinPoint, rule("normal"));

        assertEquals(new TourQuote(7L, 2, new BigDecimal("440.00"),
                new BigDecimal("20.00"), new BigDecimal("460.00")), result);
        assertEquals(new BigDecimal("0.00"), original.bookingFee());
        verify(joinPoint).proceed();
    }

    @Test
    void addFee_existingFee_accumulatesAndRoundsToTwoDecimals() throws Throwable {
        when(joinPoint.proceed()).thenReturn(quote("5.00"));

        TourQuote result = (TourQuote) aspect.addFee(joinPoint, rule("fractional"));

        assertEquals(new BigDecimal("6.01"), result.bookingFee());
        assertEquals(new BigDecimal("446.01"), result.total());
    }

    @Test
    void addFee_negativeConfiguredFee_rejectsWithoutProceed() throws Throwable {
        assertThrows(IllegalStateException.class, () -> aspect.addFee(joinPoint, rule("negative")));

        verify(joinPoint, never()).proceed();
    }

    @Test
    void addFee_wrongResultType_reportsMisconfiguredAnnotation() throws Throwable {
        when(joinPoint.proceed()).thenReturn("not a quote");

        assertThrows(IllegalStateException.class, () -> aspect.addFee(joinPoint, rule("normal")));

        verify(joinPoint).proceed();
    }

    @Test
    void addFee_targetThrows_propagatesFailureWithoutProducingQuote() throws Throwable {
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(joinPoint.proceed()).thenThrow(failure);

        Throwable result = assertThrows(IllegalStateException.class,
                () -> aspect.addFee(joinPoint, rule("normal")));

        assertSame(failure, result);
        verify(joinPoint).proceed();
    }

    private static TourQuote quote(String fee) {
        BigDecimal subtotal = new BigDecimal("440.00");
        return new TourQuote(7L, 2, subtotal, new BigDecimal(fee), subtotal.add(new BigDecimal(fee)));
    }

    private static BookingFee rule(String method) throws NoSuchMethodException {
        return Rules.class.getDeclaredMethod(method).getAnnotation(BookingFee.class);
    }

    private static class Rules {
        @BookingFee void normal() { }
        @BookingFee("1.005") void fractional() { }
        @BookingFee("-1.00") void negative() { }
    }
}
