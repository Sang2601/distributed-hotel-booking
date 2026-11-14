package com.example.paymentservice.client;

import com.example.paymentservice.dto.RoomResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RoomClient {

    private final RestTemplate restTemplate;
    private final String roomServiceUrl;

    public RoomClient(RestTemplate restTemplate,
                      @Value("${services.room-url}") String roomServiceUrl) {
        this.restTemplate = restTemplate;
        this.roomServiceUrl = roomServiceUrl;
    }

    @Retry(name = "downstream")
    @CircuitBreaker(name = "roomService")
    public RoomResponse getRoom(Long roomId) {
        return restTemplate.getForObject(roomServiceUrl + "/rooms/{id}", RoomResponse.class, roomId);
    }
}
