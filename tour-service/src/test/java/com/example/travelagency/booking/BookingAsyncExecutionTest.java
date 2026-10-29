package com.example.travelagency.booking;

import com.example.travelagency.config.AsyncConfig;
import com.example.travelagency.tour.TourResponse;
import com.example.travelagency.tour.TourService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(BookingAsyncExecutionTest.TestConfig.class)
class BookingAsyncExecutionTest {
    @Autowired BookingSupportService service;
    @Autowired BookingAsyncTasks tasks;
    @Autowired TravelPartnerClient client;
    @Autowired TourService tours;
    @Autowired AsyncFailureHandler failureHandler;
    @Autowired @Qualifier("travelExecutor") ThreadPoolTaskExecutor executor;

    @BeforeEach
    void prepareMocks() {
        reset(client, tours, failureHandler);
        when(tours.findById(7L)).thenReturn(tour());
    }

    @AfterEach
    void waitForWorkers() {
        await().atMost(Duration.ofSeconds(3)).until(() -> executor.getActiveCount() == 0
                && executor.getThreadPoolExecutor().getQueue().isEmpty());
    }

    @Test
    void confirmation_returnsWhileWorkerIsStillBlockedOnTheCustomExecutor() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> worker = new AtomicReference<>();
        doAnswer(invocation -> {
            worker.set(Thread.currentThread().getName());
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } finally {
                finished.countDown();
            }
            return null;
        }).when(client).sendConfirmation(7L, "traveler@example.com", false);

        try {
            service.queueConfirmation(7L, new ConfirmationRequest("traveler@example.com", false));

            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(1, finished.getCount(), "Caller returned before the slow operation finished");
            assertTrue(worker.get().startsWith("travel-async-"));
            assertNotEquals(Thread.currentThread().getName(), worker.get());
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(2, executor.getMaxPoolSize());
            assertEquals(2, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(2, TimeUnit.SECONDS));
    }

    @Test
    void extras_bothWorkersEnterBeforeEitherIsReleased_andCombineTheirPrices() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Set<String> threads = ConcurrentHashMap.newKeySet();
        when(client.transferPrice(7L, 2)).thenAnswer(invocation -> {
            threads.add(Thread.currentThread().getName());
            bothStarted.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new BigDecimal("50.00");
        });
        when(client.insurancePrice(7L, 2, false)).thenAnswer(invocation -> {
            threads.add(Thread.currentThread().getName());
            bothStarted.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new BigDecimal("30.00");
        });

        try {
            CompletableFuture<ExtrasQuote> future = service.extras(7L, 2, false);

            assertTrue(bothStarted.await(2, TimeUnit.SECONDS), "Both calls must enter before either can finish");
            assertFalse(future.isDone());
            assertEquals(2, threads.size());
            assertTrue(threads.stream().allMatch(name -> name.startsWith("travel-async-")));
            release.countDown();
            ExtrasQuote result = future.get(3, TimeUnit.SECONDS);
            assertTrue(result.available());
            assertEquals(new BigDecimal("50.00"), result.transferPrice());
            assertEquals(new BigDecimal("30.00"), result.insurancePrice());
            assertEquals(new BigDecimal("80.00"), result.total());
        } finally {
            release.countDown();
        }
    }

    @Test
    void extras_failedAsyncProvider_returnsFallbackInsteadOfAFailedFuture() throws Exception {
        when(client.transferPrice(7L, 2)).thenReturn(new BigDecimal("50.00"));
        when(client.insurancePrice(7L, 2, true)).thenThrow(new IllegalStateException("provider failed"));

        ExtrasQuote result = service.extras(7L, 2, true).get(3, TimeUnit.SECONDS);

        assertFalse(result.available());
        assertNull(result.total());
        assertEquals("Additional services temporarily unavailable. Please try again later.", result.message());
    }

    @Test
    void confirmation_voidFailure_reachesTheRealUncaughtExceptionHandler() {
        IllegalStateException failure = new IllegalStateException("SMTP failed");
        doThrow(failure).when(client).sendConfirmation(7L, "traveler@example.com", true);

        tasks.sendConfirmation(7L, "traveler@example.com", true);

        verify(failureHandler, timeout(2000)).handleUncaughtException(same(failure),
                argThat(method -> method.getName().equals("sendConfirmation")),
                eq(7L), eq("traveler@example.com"), eq(true));
    }

    static TourResponse tour() {
        return new TourResponse(7L, "Rome", "Italy", "Rome", "Guided tour",
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5), new BigDecimal("220.00"), 10, false);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AsyncConfig.class, BookingAsyncTasks.class, BookingSupportService.class})
    static class TestConfig {
        @Bean TravelPartnerClient client() { return mock(TravelPartnerClient.class); }
        @Bean TourService tours() { return mock(TourService.class); }
        @Bean AsyncFailureHandler handler() { return spy(new AsyncFailureHandler()); }
    }
}
