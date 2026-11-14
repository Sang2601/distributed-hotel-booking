package com.example.bookingservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Chương 5 + 8:
     *  - @LoadBalanced: URL dùng tên logic (http://room-service/...). Spring Cloud LoadBalancer hỏi Eureka
     *    danh sách instance còn sống và chia request xoay vòng (round-robin) giữa chúng.
     *  - Timeout: một instance chậm không giữ luồng của service này mãi mãi.
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(500))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }
}
