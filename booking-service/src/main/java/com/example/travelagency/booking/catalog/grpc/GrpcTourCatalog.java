package com.example.travelagency.booking.catalog.grpc;

import com.example.travelagency.booking.catalog.*;
import com.example.travelagency.contract.catalog.*;
import com.example.travelagency.contract.grpc.TourCatalogGrpc;
import com.example.travelagency.contract.grpc.TourRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class GrpcTourCatalog implements TourCatalog {
    private final TourCatalogGrpc.TourCatalogBlockingStub stub;
    private final Duration deadline;

    public GrpcTourCatalog(TourCatalogGrpc.TourCatalogBlockingStub stub, Duration deadline) {
        this.stub = stub;
        this.deadline = deadline;
    }

    @Override
    public TourPrice price(long tourId) {
        return call(tourId, () -> {
            var reply = timedStub().getPrice(request(tourId));
            return new TourPrice(reply.getTourId(), new BigDecimal(reply.getPrice()));
        });
    }

    @Override
    public TourAvailability availability(long tourId) {
        return call(tourId, () -> {
            var reply = timedStub().getAvailability(request(tourId));
            return new TourAvailability(reply.getTourId(), reply.getAvailableSeats());
        });
    }

    @Override
    public TourItinerary itinerary(long tourId) {
        return call(tourId, () -> {
            var reply = timedStub().getItinerary(request(tourId));
            return new TourItinerary(reply.getTourId(), reply.getTitle(), reply.getCountry(), reply.getCity(),
                    LocalDate.parse(reply.getStartDate()), LocalDate.parse(reply.getEndDate()));
        });
    }

    private TourCatalogGrpc.TourCatalogBlockingStub timedStub() {
        return stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
    }

    private TourRequest request(long tourId) {
        return TourRequest.newBuilder().setTourId(tourId).build();
    }

    private <T> T call(long tourId, Supplier<T> request) {
        try {
            return request.get();
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new TourLookupNotFoundException(tourId, exception);
            }
            throw new TourLookupUnavailableException(exception);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new TourLookupUnavailableException(exception);
        }
    }
}
