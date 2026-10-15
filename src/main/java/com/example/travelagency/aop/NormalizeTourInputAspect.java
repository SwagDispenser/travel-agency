package com.example.travelagency.aop;

import com.example.travelagency.tour.TourRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(20)
public class NormalizeTourInputAspect {

    @Around("@annotation(com.example.travelagency.aop.NormalizeTourInput)")
    public Object normalize(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] arguments = joinPoint.getArgs().clone();
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof TourRequest request) {
                arguments[i] = new TourRequest(strip(request.title()), strip(request.country()),
                        strip(request.city()), strip(request.description()), request.startDate(),
                        request.endDate(), request.price(), request.availableSeats(), request.featured());
            }
        }
        return joinPoint.proceed(arguments);
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
