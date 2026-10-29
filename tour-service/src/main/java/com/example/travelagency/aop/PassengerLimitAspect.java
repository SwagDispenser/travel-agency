package com.example.travelagency.aop;

import com.example.travelagency.tour.InvalidPassengerCountException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(10)
public class PassengerLimitAspect {

    @Around(value = "@annotation(rule)", argNames = "joinPoint,rule")
    public Object validate(ProceedingJoinPoint joinPoint, PassengerLimit rule) throws Throwable {
        Object[] arguments = joinPoint.getArgs();
        int index = rule.argumentIndex();
        if (index < 0 || index >= arguments.length || !(arguments[index] instanceof Integer)) {
            throw new IllegalStateException("@PassengerLimit must reference an int passenger argument");
        }
        if (rule.min() < 1 || rule.max() < rule.min()) {
            throw new IllegalStateException("@PassengerLimit has an invalid allowed range");
        }
        int passengers = (Integer) arguments[index];
        if (passengers < rule.min() || passengers > rule.max()) {
            throw new InvalidPassengerCountException(
                    "Passenger count must be between " + rule.min() + " and " + rule.max());
        }
        return joinPoint.proceed();
    }
}
