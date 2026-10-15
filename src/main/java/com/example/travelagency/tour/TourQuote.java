package com.example.travelagency.tour;

import java.math.BigDecimal;

public record TourQuote(Long tourId, int passengers, BigDecimal subtotal,
                        BigDecimal bookingFee, BigDecimal total) {
}
