package com.example.travelagency.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ConfirmationRequest(@NotBlank @Email String email, boolean simulateFailure) {
}
