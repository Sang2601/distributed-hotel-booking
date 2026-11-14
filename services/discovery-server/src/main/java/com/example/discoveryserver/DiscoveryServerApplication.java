package com.example.discoveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Chương 5 – Service Registry.
 * Mỗi instance của mọi service khi khởi động tự đăng ký tên + IP + cổng vào đây,
 * và gửi heartbeat mỗi 5 giây. Service khác hỏi Eureka "room-service đang ở đâu?"
 * để nhận danh sách instance còn sống, rồi tự cân bằng tải giữa chúng.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
