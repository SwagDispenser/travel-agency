package com.example.travelagency.booking;

import java.math.BigDecimal;

public record ExtrasQuote(Long tourId, int passengers, BigDecimal transferPrice,
                          BigDecimal insurancePrice, BigDecimal total,
                          boolean available, String message) {
}
