package com.example.travelagency.web;

import com.example.travelagency.config.RestExceptionHandler;
import com.example.travelagency.config.WebMvcConfig;
import com.example.travelagency.tour.TourRequest;
import com.example.travelagency.tour.TourResponse;
import com.example.travelagency.tour.TourRestController;
import com.example.travelagency.tour.TourService;
import com.example.travelagency.web.annotation.CreatedPost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CustomAnnotationsHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomAnnotationsHttpTest {
    @Autowired TestRestTemplate http;
    @MockBean TourService service;

    @ParameterizedTest
    @ValueSource(strings = {"/api/tours", "/test/composed-tours", "/test/plain-tours"})
    void create_composedAndSeparateAnnotations_haveTheSamePostRoutingStatusAndBody(String path) {
        TourRequest request = request(LocalDate.of(2030, 6, 5));
        TourResponse expected = new TourResponse(7L, request.title(), request.country(), request.city(),
                request.description(), request.startDate(), request.endDate(), request.price(),
                request.availableSeats(), request.featured());
        when(service.create(request)).thenReturn(expected);

        var response = http.withBasicAuth("dmytro", "test-password")
                .postForEntity(path, request, TourResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(service).create(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/tours", "/test/composed-tours", "/test/plain-tours"})
    void create_composedAndSeparateAnnotations_rejectPutAtTheCollectionPath(String path) {
        var response = http.withBasicAuth("dmytro", "test-password")
                .exchange(path, HttpMethod.PUT, new HttpEntity<>(request(LocalDate.of(2030, 6, 5))), String.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/tours", "PUT,/api/tours/7"})
    void save_reversedDates_returnsClearValidationErrorWithoutCallingService(HttpMethod method, String path) {
        TourRequest request = request(LocalDate.of(2030, 5, 31));

        var response = http.withBasicAuth("dmytro", "test-password")
                .exchange(path, method, new HttpEntity<>(request), ProblemDetail.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation failed", response.getBody().getTitle());
        assertEquals("endDate: Tour end date must be on or after start date", response.getBody().getDetail());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dmytro", "olena"})
    void currentTraveler_realAuthenticatedHttpRequest_returnsThatUsersResolvedId(String userId) {
        var response = http.withBasicAuth(userId, "test-password")
                .getForEntity("/api/travelers/me", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("userId", userId), response.getBody());
    }

    @Test
    void currentTraveler_queryParameterCannotOverrideAuthenticatedIdentity() {
        var response = http.withBasicAuth("dmytro", "test-password")
                .getForEntity("/api/travelers/me?userId=someone-else", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("userId", "dmytro"), response.getBody());
    }

    @Test
    void currentTraveler_unauthenticatedHttpRequest_returnsUnauthorized() {
        var response = http.getForEntity("/api/travelers/me", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private static TourRequest request(LocalDate endDate) {
        return new TourRequest("Rome", "Italy", "Rome", "Guided tour", LocalDate.of(2030, 6, 1), endDate,
                new BigDecimal("450.00"), 5, false);
    }

    // A real HTTP server with production MVC components, but no database or Keycloak connection.
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            LiquibaseAutoConfiguration.class, OAuth2ClientAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class})
    @Import({TourRestController.class, TravelerController.class, WebMvcConfig.class,
            RestExceptionHandler.class, ComparisonController.class})
    static class TestApplication {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/test/**"))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("dmytro").password("{noop}test-password").roles("USER").build(),
                    User.withUsername("olena").password("{noop}test-password").roles("USER").build());
        }

        @Bean
        ValidationConfigurationCustomizer fixedValidationClock() {
            return configuration -> configuration.clockProvider(
                    () -> Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        }
    }

    @RestController
    static class ComparisonController {
        private final TourService service;

        ComparisonController(TourService service) {
            this.service = service;
        }

        @CreatedPost("/test/composed-tours")
        TourResponse composed(@RequestBody TourRequest request) {
            return service.create(request);
        }

        @PostMapping("/test/plain-tours")
        @ResponseStatus(HttpStatus.CREATED)
        TourResponse separate(@RequestBody TourRequest request) {
            return service.create(request);
        }
    }
}
