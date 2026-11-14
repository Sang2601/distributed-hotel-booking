package com.example.paymentservice.controller;

import com.example.paymentservice.client.BookingClient;
import com.example.paymentservice.client.RoomClient;
import com.example.paymentservice.dto.BookingResponse;
import com.example.paymentservice.dto.RoomResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.messaging.PaymentEventPublisher;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Random;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final String SERVICE_DOWN_MESSAGE =
            "❌ Hệ thống tạm thời gián đoạn, vui lòng thử lại sau ít phút";

    private final PaymentRepository paymentRepository;
    private final BookingClient bookingClient;
    private final RoomClient roomClient;
    private final PaymentEventPublisher eventPublisher;
    private final Random random = new Random();

    public PaymentController(PaymentRepository paymentRepository,
                             BookingClient bookingClient,
                             RoomClient roomClient,
                             PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.bookingClient = bookingClient;
        this.roomClient = roomClient;
        this.eventPublisher = eventPublisher;
    }

    // ================== PAY ==================
    @PostMapping
    public ResponseEntity<?> pay(@RequestBody Payment payment) {

        if (payment.getBookingId() == null) {
            return ResponseEntity.badRequest().body("❌ Thiếu mã booking");
        }

        // 1️⃣ LẤY BOOKING (gọi thẳng booking-service, có retry + circuit breaker)
        BookingResponse booking;
        try {
            booking = bookingClient.getBooking(payment.getBookingId());
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.badRequest().body("❌ Booking không tồn tại");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SERVICE_DOWN_MESSAGE);
        }

        // 2️⃣ CHECK TRẠNG THÁI BOOKING
        if ("CANCELLED".equals(booking.getStatus())) {
            return ResponseEntity.badRequest().body("❌ Booking đã bị huỷ");
        }
        if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
            return ResponseEntity.badRequest().body("❌ Booking không hợp lệ để thanh toán");
        }

        // 3️⃣ LẤY GIÁ PHÒNG TỪ ROOM SERVICE (BACKEND QUYẾT ĐỊNH)
        RoomResponse room;
        try {
            room = roomClient.getRoom(booking.getRoomId());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("❌ Không lấy được thông tin phòng");
        }
        if (room == null || room.getPrice() == null) {
            return ResponseEntity.badRequest().body("❌ Giá phòng không hợp lệ");
        }

        // 4️⃣ GIỮ QUYỀN THANH TOÁN (Chương 6): PENDING_PAYMENT → PAYING một cách nguyên tử.
        //     Hai lần bấm thanh toán đồng thời: chỉ một lần qua được bước này, lần kia nhận 409.
        try {
            bookingClient.startPayment(booking.getId());
        } catch (HttpClientErrorException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("❌ Booking đang được thanh toán hoặc đã thanh toán");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SERVICE_DOWN_MESSAGE);
        }

        // 5️⃣ SET DỮ LIỆU PAYMENT (BACKEND TOÀN QUYỀN) + GIẢ LẬP THANH TOÁN
        payment.setBookingId(booking.getId());
        payment.setAmount(room.getPrice());
        boolean success = random.nextInt(10) < 7;
        payment.setStatus(success ? "SUCCESS" : "FAILED");

        Payment savedPayment = paymentRepository.save(payment);

        // 6️⃣ PHÁT SỰ KIỆN cho booking-service (bất đồng bộ qua RabbitMQ)
        eventPublisher.publishCompleted(savedPayment);

        // 7️⃣ TRẢ KẾT QUẢ
        return ResponseEntity.ok(savedPayment);
    }

    // ================== REVENUE ==================
    @GetMapping("/revenue/total")
    public Double getTotalRevenue() {
        return paymentRepository.getTotalRevenue();
    }
}
