package com.example.travelagency.aop;

import com.example.travelagency.config.RestExceptionHandler;
import com.example.travelagency.tour.*;
import com.example.travelagency.web.AopDemoController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Complements the context-free advice tests with real HTTP and Boot-created AOP proxies.
@SpringBootTest(classes = AopHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AopHttpTest {
    @Autowired TestRestTemplate http;
    @MockBean TourRepository repository;

    @Test
    void quote_normalEndpoint_returnsFeeAdjustedJson() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour(12)));

        var response = http.withBasicAuth("user", "test-password")
                .getForEntity("/api/tours/7/quote?passengers=2", TourQuote.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("20.00"), response.getBody().bookingFee());
        assertEquals(new BigDecimal("460.00"), response.getBody().total());
    }

    @Test
    void demo_sameQuoteThroughExternalAndSelfPaths_showsFeeDifference() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour(12)));

        var external = http.withBasicAuth("admin", "test-password")
                .getForEntity("/admin/aop-demo/tours/7/external?passengers=2", TourQuote.class);
        var self = http.withBasicAuth("admin", "test-password")
                .getForEntity("/admin/aop-demo/tours/7/self-invocation?passengers=2", TourQuote.class);

        assertEquals(HttpStatus.OK, external.getStatusCode());
        assertEquals(HttpStatus.OK, self.getStatusCode());
        assertNotNull(external.getBody());
        assertNotNull(self.getBody());
        assertEquals(new BigDecimal("460.00"), external.getBody().total());
        assertEquals(new BigDecimal("440.00"), self.getBody().total());
        assertEquals(new BigDecimal("0.00"), self.getBody().bookingFee());
    }

    @Test
    void quote_zeroPassengers_returnsBadRequestBeforeLoadingTour() {
        var response = http.withBasicAuth("user", "test-password")
                .getForEntity("/api/tours/7/quote?passengers=0", ProblemDetail.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Passenger count must be between 1 and 10", response.getBody().getDetail());
        verifyNoInteractions(repository);
    }

    @Test
    void demo_selfInvocationWithZeroPassengers_silentlySkipsGuard() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour(12)));

        var response = http.withBasicAuth("admin", "test-password")
                .getForEntity("/admin/aop-demo/tours/7/self-invocation?passengers=0", TourQuote.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("0.00"), response.getBody().total());
    }

    @Test
    void create_textWithSurroundingSpaces_returnsAndSavesNormalizedText() {
        TourRequest request = new TourRequest(" Rome ", " Italy ", " Rome ", " City tour ",
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5), new BigDecimal("220.00"), 5, false);
        when(repository.save(any())).thenAnswer(invocation -> {
            Tour saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        var response = http.withBasicAuth("admin", "test-password")
                .postForEntity("/api/tours", request, TourResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Rome", response.getBody().title());
        ArgumentCaptor<Tour> saved = ArgumentCaptor.forClass(Tour.class);
        verify(repository).save(saved.capture());
        assertEquals("Italy", saved.getValue().getCountry());
        assertEquals("City tour", saved.getValue().getDescription());
    }

    @Test
    void quote_notEnoughSeats_returnsClearBadRequest() {
        when(repository.findById(7L)).thenReturn(Optional.of(tour(1)));

        var response = http.withBasicAuth("user", "test-password")
                .getForEntity("/api/tours/7/quote?passengers=2", ProblemDetail.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Not enough available seats for this tour", response.getBody().getDetail());
    }

    @Test
    void demo_regularUser_cannotAccessIntentionalBypass() {
        var response = http.withBasicAuth("user", "test-password")
                .getForEntity("/admin/aop-demo/tours/7/self-invocation", String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(repository);
    }

    private static Tour tour(int seats) {
        Tour tour = new Tour();
        tour.setId(7L);
        tour.setPrice(new BigDecimal("220.00"));
        tour.setAvailableSeats(seats);
        return tour;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            LiquibaseAutoConfiguration.class, OAuth2ClientAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class})
    @Import({TourService.class, TourMapperImpl.class, TourRestController.class, AopDemoController.class,
            RestExceptionHandler.class, NormalizeTourInputAspect.class,
            PassengerLimitAspect.class, BookingFeeAspect.class})
    static class TestApplication {
        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                    .authorizeHttpRequests(auth -> auth.requestMatchers("/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults()).build();
        }

        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("admin").password("{noop}test-password").roles("ADMIN").build(),
                    User.withUsername("user").password("{noop}test-password").roles("USER").build());
        }

        @Bean
        ValidationConfigurationCustomizer validationClock() {
            return configuration -> configuration.clockProvider(
                    () -> Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        }
    }
}
