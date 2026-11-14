package com.example.paymentservice.client;

import com.example.paymentservice.dto.BookingResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BookingClient {

    private final RestTemplate restTemplate;
    private final String bookingServiceUrl;

    public BookingClient(RestTemplate restTemplate,
                         @Value("${services.booking-url}") String bookingServiceUrl) {
        this.restTemplate = restTemplate;
        this.bookingServiceUrl = bookingServiceUrl;
    }

    /** Đọc dữ liệu: an toàn để thử lại. */
    @Retry(name = "downstream")
    @CircuitBreaker(name = "bookingService")
    public BookingResponse getBooking(Long bookingId) {
        return restTemplate.getForObject(bookingServiceUrl + "/bookings/{id}", BookingResponse.class, bookingId);
    }

    /**
     * Giữ quyền thanh toán (PENDING_PAYMENT → PAYING). Không tự thử lại: nếu lần đầu thực ra đã thành công
     * mà mất phản hồi, lần thử lại sẽ nhận 409 và người dùng bị báo lỗi sai.
     */
    @CircuitBreaker(name = "bookingService")
    public void startPayment(Long bookingId) {
        restTemplate.put(bookingServiceUrl + "/bookings/{id}/start-payment", null, bookingId);
    }

    /** Đường dự phòng khi không gửi được sự kiện lên RabbitMQ. Booking-service xử lý idempotent nên thử lại được. */
    @Retry(name = "downstream")
    @CircuitBreaker(name = "bookingService")
    public void applyPaymentResult(Long bookingId, boolean success) {
        String action = success ? "confirm" : "fail";
        restTemplate.put(bookingServiceUrl + "/bookings/{id}/" + action, null, bookingId);
    }
}
