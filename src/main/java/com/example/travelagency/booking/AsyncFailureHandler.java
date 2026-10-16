package com.example.travelagency.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class AsyncFailureHandler implements AsyncUncaughtExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(AsyncFailureHandler.class);

    @Override
    public void handleUncaughtException(Throwable exception, Method method, Object... parameters) {
        log.error("Background operation {} failed on {}", method.getName(),
                Thread.currentThread().getName(), exception);
    }
}
