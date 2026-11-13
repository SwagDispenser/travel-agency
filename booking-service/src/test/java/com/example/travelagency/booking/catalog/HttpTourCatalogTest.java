package com.example.travelagency.booking.catalog;

import com.example.travelagency.booking.catalog.http.HttpTourCatalog;
import com.example.travelagency.contract.catalog.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class HttpTourCatalogTest {
    HttpServer server;
    ExecutorService executor;
    TourCatalog client;
    AtomicReference<String> path = new AtomicReference<>();
    int status = 200;
    String body = "";
    CountDownLatch responseGate = new CountDownLatch(0);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            try {
                responseGate.await(2, TimeUnit.SECONDS);
                byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = client(server.getAddress().getPort());
    }

    private TourCatalog client(int port) {
        var properties = new TourClientProperties(TourClientProperties.Protocol.HTTP,
                URI.create("http://localhost:" + port), "localhost", 9090, true,
                Duration.ofMillis(200), Duration.ofMillis(200), Duration.ofSeconds(1));
        return new HttpTourCatalog(TourClientConfig.httpClient(properties));
    }

    @AfterEach
    void stop() {
        responseGate.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void allThreeDeclarativeMethodsDecodeExactValuesAndUseExpectedPaths() {
        body = "{\"tourId\":7,\"price\":123.45}";
        assertThat(client.price(7)).isEqualTo(new TourPrice(7, new BigDecimal("123.45")));
        assertThat(path.get()).isEqualTo("/api/catalog/tours/7/price");
        body = "{\"tourId\":7,\"availableSeats\":9}";
        assertThat(client.availability(7)).isEqualTo(new TourAvailability(7, 9));
        assertThat(path.get()).isEqualTo("/api/catalog/tours/7/availability");
        body = "{\"tourId\":7,\"title\":\"Kyiv\",\"country\":\"Ukraine\",\"city\":\"Kyiv\","
                + "\"startDate\":\"2027-01-01\",\"endDate\":\"2027-01-05\"}";
        assertThat(client.itinerary(7)).isEqualTo(new TourItinerary(7, "Kyiv", "Ukraine", "Kyiv",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5)));
        assertThat(path.get()).isEqualTo("/api/catalog/tours/7/itinerary");
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "availability", "itinerary"})
    void everyOperationMaps404ToOwnNotFound(String operation) {
        status = 404;
        assertThatThrownBy(() -> invoke(operation)).isInstanceOf(TourLookupNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 502, 503, 504})
    void everyNon404HttpErrorIsUnavailable(int code) {
        status = code;
        for (String operation : new String[]{"price", "availability", "itinerary"}) {
            assertThatThrownBy(() -> invoke(operation)).isInstanceOf(TourLookupUnavailableException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "availability", "itinerary"})
    void slowResponseIsBoundedByConfiguredReadTimeout(String operation) {
        responseGate = new CountDownLatch(1);
        long started = System.nanoTime();
        assertThatThrownBy(() -> invoke(operation)).isInstanceOf(TourLookupUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void refusedConnectionIsUnavailable() throws Exception {
        int unused;
        try (var socket = new java.net.ServerSocket(0)) { unused = socket.getLocalPort(); }
        assertThatThrownBy(() -> client(unused).price(7)).isInstanceOf(TourLookupUnavailableException.class);
    }

    @Test
    void malformedOrEmptyResponseIsUnavailable() {
        body = "not json";
        assertThatThrownBy(() -> client.price(7)).isInstanceOf(TourLookupUnavailableException.class);
        body = "";
        assertThatThrownBy(() -> client.price(7)).isInstanceOf(TourLookupUnavailableException.class);
    }

    private Object invoke(String operation) {
        return switch (operation) {
            case "price" -> client.price(7);
            case "availability" -> client.availability(7);
            default -> client.itinerary(7);
        };
    }
}
