package com.example.bookingservice.dto;

import java.time.LocalDate;

public record CreateBookingRequest(
        Long roomId,
        LocalDate checkIn,
        LocalDate checkOut,
        String userEmail
) {
}
