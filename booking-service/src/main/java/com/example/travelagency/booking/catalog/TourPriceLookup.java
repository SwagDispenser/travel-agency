package com.example.travelagency.booking.catalog;

import com.example.travelagency.contract.catalog.TourPrice;

public interface TourPriceLookup {
    TourPrice price(long tourId);
}
