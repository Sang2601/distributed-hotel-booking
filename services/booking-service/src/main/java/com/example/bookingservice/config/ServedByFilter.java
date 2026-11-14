package com.example.bookingservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Chương 8 – Minh hoạ cân bằng tải: mỗi response mang header X-Served-By = tên container
 * đã xử lý request. Gọi nhiều lần sẽ thấy giá trị luân phiên giữa các instance.
 */
@Component
public class ServedByFilter extends OncePerRequestFilter {

    private final String instanceName;

    public ServedByFilter() {
        String name;
        try {
            name = "booking-service@" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            name = "booking-service@unknown";
        }
        this.instanceName = name;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Served-By", instanceName);
        chain.doFilter(request, response);
    }
}
