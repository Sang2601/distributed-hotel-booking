package com.example.bookingservice.client;

import com.example.bookingservice.dto.RoomResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Chương 8 – Circuit breaker.
 * Nếu room-service chết hoặc chậm liên tục, circuit chuyển sang OPEN và các lần gọi sau
 * trả về fallback ngay lập tức thay vì chờ timeout. Trang admin vẫn hiển thị booking,
 * chỉ thiếu tên phòng ("N/A"). Sau 10 giây circuit thử HALF_OPEN để xem room-service đã hồi chưa.
 */
@Component
public class RoomClient {

    private static final Logger log = LoggerFactory.getLogger(RoomClient.class);

    private final RestTemplate restTemplate;
    private final String roomServiceUrl;

    public RoomClient(RestTemplate restTemplate, @Value("${services.room-url}") String roomServiceUrl) {
        this.restTemplate = restTemplate;
        this.roomServiceUrl = roomServiceUrl;
    }

    @CircuitBreaker(name = "roomService", fallbackMethod = "getRoomFallback")
    public RoomResponse getRoom(Long roomId) {
        return restTemplate.getForObject(roomServiceUrl + "/rooms/{id}", RoomResponse.class, roomId);
    }

    public RoomResponse getRoomFallback(Long roomId, Throwable error) {
        log.warn("Không lấy được phòng {} từ room-service: {}", roomId, error.toString());
        return null;
    }
}
