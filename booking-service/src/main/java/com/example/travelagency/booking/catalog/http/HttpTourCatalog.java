package com.example.travelagency.booking.catalog.http;

import com.example.travelagency.booking.catalog.*;
import com.example.travelagency.contract.catalog.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

public class HttpTourCatalog implements TourCatalog {
    private final TourHttpClient client;

    public HttpTourCatalog(TourHttpClient client) {
        this.client = client;
    }

    @Override
    public TourPrice price(long tourId) {
        return call(tourId, () -> client.price(tourId));
    }

    @Override
    public TourAvailability availability(long tourId) {
        return call(tourId, () -> client.availability(tourId));
    }

    @Override
    public TourItinerary itinerary(long tourId) {
        return call(tourId, () -> client.itinerary(tourId));
    }

    private <T> T call(long tourId, Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new TourLookupUnavailableException(null);
            }
            return result;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new TourLookupNotFoundException(tourId, exception);
            }
            throw new TourLookupUnavailableException(exception);
        } catch (RestClientException exception) {
            throw new TourLookupUnavailableException(exception);
        }
    }
}
