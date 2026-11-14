package com.example.bookingservice.messaging;

/** Sự kiện payment-service phát ra sau mỗi lần thanh toán (status = SUCCESS / FAILED). */
public record PaymentCompletedEvent(
        Long paymentId,
        Long bookingId,
        String status,
        Double amount
) {
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
