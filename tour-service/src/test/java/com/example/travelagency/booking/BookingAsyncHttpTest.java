package com.example.travelagency.booking;

import com.example.travelagency.config.AsyncConfig;
import com.example.travelagency.config.RestExceptionHandler;
import com.example.travelagency.tour.TourService;
import com.example.travelagency.web.BookingSupportController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = BookingAsyncHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"travel.async.core-pool-size=1", "travel.async.max-pool-size=1",
                "travel.async.queue-capacity=2", "travel.async.timeout-ms=300"})
class BookingAsyncHttpTest {
    private static final String ROOT = "/api/tours/7/booking-support";
    @Autowired TestRestTemplate http;
    @Autowired @Qualifier("travelExecutor") ThreadPoolTaskExecutor executor;
    @MockBean TourService tours;
    @MockBean TravelPartnerClient client;

    @BeforeEach
    void prepareTour() {
        when(tours.findById(7L)).thenReturn(BookingAsyncExecutionTest.tour());
    }

    @AfterEach
    void waitForWorkers() {
        await().atMost(Duration.ofSeconds(3)).until(() -> executor.getActiveCount() == 0
                && executor.getThreadPoolExecutor().getQueue().isEmpty());
    }

    @Test
    void saturatedExecutor_oneRunningAndTwoQueued_fourthRequestReturns503WithRetryAdvice() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(client).sendConfirmation(7L, "traveler@example.com", false);
        ConfirmationRequest request = new ConfirmationRequest("traveler@example.com", false);

        try {
            var first = http.withBasicAuth("user", "test-password")
                    .postForEntity(ROOT + "/confirmation", request, Map.class);
            assertEquals(HttpStatus.ACCEPTED, first.getStatusCode());
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            var queued = http.withBasicAuth("user", "test-password")
                    .postForEntity(ROOT + "/confirmation", request, Map.class);
            assertEquals(HttpStatus.ACCEPTED, queued.getStatusCode());
            assertEquals(1, executor.getThreadPoolExecutor().getQueue().size());

            var secondQueued = http.withBasicAuth("user", "test-password")
                    .postForEntity(ROOT + "/confirmation", request, Map.class);
            assertEquals(HttpStatus.ACCEPTED, secondQueued.getStatusCode());
            assertEquals(2, executor.getThreadPoolExecutor().getQueue().size());

            var rejected = http.withBasicAuth("user", "test-password")
                    .postForEntity(ROOT + "/confirmation", request, ProblemDetail.class);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rejected.getStatusCode());
            assertEquals("1", rejected.getHeaders().getFirst("Retry-After"));
            assertNotNull(rejected.getBody());
            assertEquals("Background workers are busy. Please try again later.", rejected.getBody().getDetail());
        } finally {
            release.countDown();
        }
    }

    @Test
    void extras_success_returnsCombinedJson() {
        when(client.transferPrice(7L, 2)).thenReturn(new BigDecimal("50.00"));
        when(client.insurancePrice(7L, 2, false)).thenReturn(new BigDecimal("30.00"));

        var response = http.withBasicAuth("user", "test-password")
                .getForEntity(ROOT + "/extras?passengers=2", ExtrasQuote.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().available());
        assertEquals(new BigDecimal("80.00"), response.getBody().total());
    }

    @Test
    void extras_failedFuture_stillReturns200WithAnExplicitUnavailableResult() {
        when(client.transferPrice(7L, 2)).thenReturn(new BigDecimal("50.00"));
        when(client.insurancePrice(7L, 2, true)).thenThrow(new IllegalStateException("insurance offline"));

        var response = http.withBasicAuth("user", "test-password")
                .getForEntity(ROOT + "/extras?passengers=2&simulateInsuranceFailure=true", ExtrasQuote.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().available());
        assertNull(response.getBody().total());
        assertTrue(response.getBody().message().contains("Please try again later"));
    }

    @Test
    void extras_blockedProvider_returnsFallbackWithoutWaitingForWorkerCompletion() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(client.transferPrice(7L, 2)).thenAnswer(invocation -> {
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new BigDecimal("50.00");
        });
        when(client.insurancePrice(7L, 2, false)).thenReturn(new BigDecimal("30.00"));

        try {
            var response = http.withBasicAuth("user", "test-password")
                    .getForEntity(ROOT + "/extras?passengers=2", ExtrasQuote.class);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().available());
            assertEquals(1, release.getCount(), "HTTP returned before the provider was released");
        } finally {
            release.countDown();
        }
    }

    @Test
    void confirmation_invalidEmail_returns400WithoutSubmittingAnything() {
        var response = http.withBasicAuth("user", "test-password")
                .postForEntity(ROOT + "/confirmation", new ConfirmationRequest("not-an-email", false), ProblemDetail.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(client, tours);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            LiquibaseAutoConfiguration.class, OAuth2ClientAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class})
    @Import({AsyncConfig.class, AsyncFailureHandler.class, BookingAsyncTasks.class, BookingSupportService.class,
            BookingSupportController.class, RestExceptionHandler.class})
    static class TestApplication {
        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults()).build();
        }

        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("user").password("{noop}test-password").roles("USER").build());
        }
    }
}
