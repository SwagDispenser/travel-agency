package com.example.travelagency.catalog;

import com.example.travelagency.catalog.grpc.*;
import com.example.travelagency.config.RestExceptionHandler;
import com.example.travelagency.contract.grpc.*;
import com.example.travelagency.tour.TourService;
import com.example.travelagency.tour.TourResponse;
import com.example.travelagency.tour.TourNotFoundException;
import io.grpc.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TourCatalogTransportTest {
    TourService tours = mock(TourService.class);
    TourCatalogService catalog = new TourCatalogService(tours);
    ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(TourGrpcConfig.class)
            .withBean(TourCatalogService.class, () -> catalog);

    @BeforeEach
    void data() {
        when(tours.findById(7L)).thenReturn(new TourResponse(7L, "Kyiv", "Ukraine", "Kyiv", "City break",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5), new BigDecimal("123.45"), 9, false));
        when(tours.findById(99L)).thenThrow(new TourNotFoundException(99L));
    }

    @Test
    void restExposesAllThreeLookupsAnd404() throws Exception {
        MockMvc mvc = standaloneSetup(new TourCatalogController(catalog))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()))
                .setControllerAdvice(new RestExceptionHandler()).build();
        mvc.perform(get("/api/catalog/tours/7/price"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(123.45));
        mvc.perform(get("/api/catalog/tours/7/availability"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(9));
        mvc.perform(get("/api/catalog/tours/7/itinerary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").value("2027-01-01"))
                .andExpect(jsonPath("$.title").value("Kyiv"));
        for (String action : new String[]{"price", "availability", "itinerary"}) {
            mvc.perform(get("/api/catalog/tours/99/" + action)).andExpect(status().isNotFound());
        }
    }

    @Test
    void serverIsDisabledByDefault() {
        context.run(c -> assertThat(c).doesNotHaveBean(TourGrpcServer.class));
    }

    @Test
    void enabledServerStartsWithContextServesAllMethodsAndStopsWithContext() throws Exception {
        var lifecycle = new java.util.concurrent.atomic.AtomicReference<TourGrpcServer>();
        var port = new java.util.concurrent.atomic.AtomicInteger();
        context.withPropertyValues("travel.catalog.grpc.enabled=true", "travel.catalog.grpc.port=0").run(c -> {
            var server = c.getBean(TourGrpcServer.class);
            lifecycle.set(server);
            port.set(server.port());
            assertThat(server.isRunning()).isTrue();
            assertThat(server.port()).isPositive();
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", server.port()).usePlaintext().build();
            try {
                var stub = TourCatalogGrpc.newBlockingStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS);
                var request = TourRequest.newBuilder().setTourId(7).build();
                assertThat(stub.getPrice(request).getPrice()).isEqualTo("123.45");
                assertThat(stub.getAvailability(request).getAvailableSeats()).isEqualTo(9);
                var itinerary = stub.getItinerary(request);
                assertThat(itinerary.getTitle()).isEqualTo("Kyiv");
                assertThat(itinerary.getCountry()).isEqualTo("Ukraine");
                assertThat(itinerary.getCity()).isEqualTo("Kyiv");
                assertThat(itinerary.getStartDate()).isEqualTo("2027-01-01");
                assertThat(itinerary.getEndDate()).isEqualTo("2027-01-05");
                var missing = TourRequest.newBuilder().setTourId(99).build();
                assertStatus(() -> stub.getPrice(missing), Status.Code.NOT_FOUND);
                assertStatus(() -> stub.getAvailability(missing), Status.Code.NOT_FOUND);
                assertStatus(() -> stub.getItinerary(missing), Status.Code.NOT_FOUND);
                when(tours.findById(8L)).thenThrow(new IllegalStateException("Database failed"));
                assertStatus(() -> stub.getPrice(TourRequest.newBuilder().setTourId(8).build()), Status.Code.INTERNAL);
            } finally {
                channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            }
        });
        assertThat(lifecycle.get().isRunning()).isFalse();
        // Closing the application releases its configured listening port.
        try (var socket = new java.net.ServerSocket(port.get())) {
            assertThat(socket.isBound()).isTrue();
        }
    }

    private void assertStatus(Runnable operation, Status.Code code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(StatusRuntimeException.class,
                exception -> assertThat(exception.getStatus().getCode()).isEqualTo(code));
    }
}
