package com.example.travelagency.aop;

import com.example.travelagency.tour.TourQuote;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Aspect
@Component
@Order(30)
public class BookingFeeAspect {

    @Around(value = "@annotation(rule)", argNames = "joinPoint,rule")
    public Object addFee(ProceedingJoinPoint joinPoint, BookingFee rule) throws Throwable {
        BigDecimal fee = new BigDecimal(rule.value());
        if (fee.signum() < 0) {
            throw new IllegalStateException("@BookingFee cannot be negative");
        }
        Object result = joinPoint.proceed();
        if (!(result instanceof TourQuote quote)) {
            throw new IllegalStateException("@BookingFee requires a TourQuote return value");
        }
        BigDecimal totalFee = quote.bookingFee().add(fee).setScale(2, RoundingMode.HALF_UP);
        return new TourQuote(quote.tourId(), quote.passengers(), quote.subtotal(), totalFee,
                quote.subtotal().add(totalFee));
    }
}
