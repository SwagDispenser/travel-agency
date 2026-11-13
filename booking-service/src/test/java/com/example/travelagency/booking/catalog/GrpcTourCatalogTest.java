package com.example.travelagency.booking.catalog;

import com.example.travelagency.booking.catalog.grpc.GrpcTourCatalog;
import com.example.travelagency.contract.catalog.*;
import com.example.travelagency.contract.grpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class GrpcTourCatalogTest {
    Server server;
    ManagedChannel channel;
    GrpcTourCatalog client;
    volatile Status failure;
    volatile boolean silent;
    volatile boolean deadlineSeen;

    @BeforeEach
    void start() throws Exception {
        server = ServerBuilder.forPort(0).addService(new TourCatalogGrpc.TourCatalogImplBase() {
            @Override public void getPrice(TourRequest request, StreamObserver<PriceReply> observer) {
                assertThat(request.getTourId()).isEqualTo(7);
                respond(observer, PriceReply.newBuilder().setTourId(7).setPrice("123.45").build());
            }
            @Override public void getAvailability(TourRequest request, StreamObserver<AvailabilityReply> observer) {
                assertThat(request.getTourId()).isEqualTo(7);
                respond(observer, AvailabilityReply.newBuilder().setTourId(7).setAvailableSeats(9).build());
            }
            @Override public void getItinerary(TourRequest request, StreamObserver<ItineraryReply> observer) {
                assertThat(request.getTourId()).isEqualTo(7);
                respond(observer, ItineraryReply.newBuilder().setTourId(7).setTitle("Kyiv")
                        .setCountry("Ukraine").setCity("Kyiv").setStartDate("2027-01-01").setEndDate("2027-01-05").build());
            }
        }).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        client = new GrpcTourCatalog(TourCatalogGrpc.newBlockingStub(channel), Duration.ofSeconds(2));
    }

    private <T> void respond(StreamObserver<T> observer, T value) {
        deadlineSeen = Context.current().getDeadline() != null;
        if (silent) return;
        if (failure != null) { observer.onError(failure.asRuntimeException()); return; }
        observer.onNext(value);
        observer.onCompleted();
    }

    @AfterEach
    void stop() throws Exception {
        channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
    }

    @Test
    void allMethodsMapValuesAndSetDeadlineOnEveryCall() {
        assertThat(client.price(7)).isEqualTo(new TourPrice(7, new BigDecimal("123.45")));
        assertThat(deadlineSeen).isTrue();
        deadlineSeen = false;
        assertThat(client.availability(7)).isEqualTo(new TourAvailability(7, 9));
        assertThat(deadlineSeen).isTrue();
        deadlineSeen = false;
        assertThat(client.itinerary(7)).isEqualTo(new TourItinerary(7, "Kyiv", "Ukraine", "Kyiv",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5)));
        assertThat(deadlineSeen).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = "OK", mode = EnumSource.Mode.EXCLUDE)
    void allErrorStatusesAreTranslatedForEveryOperation(Status.Code code) {
        failure = Status.fromCode(code);
        Class<? extends RuntimeException> expected = code == Status.Code.NOT_FOUND
                ? TourLookupNotFoundException.class : TourLookupUnavailableException.class;
        for (String operation : new String[]{"price", "availability", "itinerary"}) {
            assertThatThrownBy(() -> invoke(operation)).isInstanceOf(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "availability", "itinerary"})
    void unresponsiveServerCannotExceedDeadline(String operation) {
        silent = true;
        client = new GrpcTourCatalog(TourCatalogGrpc.newBlockingStub(channel), Duration.ofMillis(150));
        long started = System.nanoTime();
        assertThatThrownBy(() -> invoke(operation)).isInstanceOf(TourLookupUnavailableException.class)
                .hasCauseInstanceOf(StatusRuntimeException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void stoppedServerIsUnavailable() throws Exception {
        server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
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
