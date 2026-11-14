package com.example.paymentservice.messaging;

/** Sự kiện phát ra sau mỗi lần thanh toán (status = SUCCESS / FAILED). */
public record PaymentCompletedEvent(
        Long paymentId,
        Long bookingId,
        String status,
        Double amount
) {
}
