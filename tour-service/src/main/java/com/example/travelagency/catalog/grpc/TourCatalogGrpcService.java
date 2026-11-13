package com.example.travelagency.catalog.grpc;

import com.example.travelagency.catalog.TourCatalogService;
import com.example.travelagency.contract.grpc.*;
import com.example.travelagency.tour.TourNotFoundException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

public class TourCatalogGrpcService extends TourCatalogGrpc.TourCatalogImplBase {
    private final TourCatalogService catalog;

    public TourCatalogGrpcService(TourCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public void getPrice(TourRequest request, StreamObserver<PriceReply> observer) {
        respond(observer, () -> {
            var price = catalog.price(request.getTourId());
            return PriceReply.newBuilder().setTourId(price.tourId()).setPrice(price.price().toPlainString()).build();
        });
    }

    @Override
    public void getAvailability(TourRequest request, StreamObserver<AvailabilityReply> observer) {
        respond(observer, () -> {
            var seats = catalog.availability(request.getTourId());
            return AvailabilityReply.newBuilder().setTourId(seats.tourId())
                    .setAvailableSeats(seats.availableSeats()).build();
        });
    }

    @Override
    public void getItinerary(TourRequest request, StreamObserver<ItineraryReply> observer) {
        respond(observer, () -> {
            var tour = catalog.itinerary(request.getTourId());
            return ItineraryReply.newBuilder().setTourId(tour.tourId()).setTitle(tour.title())
                    .setCountry(tour.country()).setCity(tour.city())
                    .setStartDate(tour.startDate().toString()).setEndDate(tour.endDate().toString()).build();
        });
    }

    private <T> void respond(StreamObserver<T> observer, Supplier<T> operation) {
        final T result;
        try {
            result = operation.get();
        } catch (TourNotFoundException exception) {
            observer.onError(Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException());
            return;
        } catch (RuntimeException exception) {
            observer.onError(Status.INTERNAL.withDescription("Tour lookup failed").asRuntimeException());
            return;
        }
        observer.onNext(result);
        observer.onCompleted();
    }
}
