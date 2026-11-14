package com.example.bookingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Chương 3 – Thread pool riêng cho job @Scheduled (luồng "booking-scheduler-*").
 * Khai báo tường minh vì WebSocket cũng tạo một TaskScheduler riêng cho message broker;
 * bean tên "taskScheduler" được @Scheduled ưu tiên dùng.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("booking-scheduler-");
        return scheduler;
    }
}
