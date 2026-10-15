package com.example.travelagency.tour;

public class InvalidPassengerCountException extends RuntimeException {
    public InvalidPassengerCountException(String message) {
        super(message);
    }
}
