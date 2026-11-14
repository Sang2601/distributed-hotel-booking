package com.example.bookingservice.controller;

import com.example.bookingservice.client.RoomClient;
import com.example.bookingservice.dto.BookingAdminResponse;
import com.example.bookingservice.dto.CreateBookingRequest;
import com.example.bookingservice.dto.RoomResponse;
import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    /** Header do api-gateway gắn sau khi xác thực JWT (Chương 5). */
    private static final String USER_EMAIL = "X-User-Email";
    private static final String USER_ROLE = "X-User-Role";

    private final BookingService bookingService;
    private final RoomClient roomClient;

    public BookingController(BookingService bookingService, RoomClient roomClient) {
        this.bookingService = bookingService;
        this.roomClient = roomClient;
    }

    // ================= USER BOOKING =================

    @PostMapping(consumes = "application/json")
    public Booking createBooking(
            @RequestBody CreateBookingRequest request,
            @RequestHeader(value = USER_EMAIL, required = false) String authEmail
    ) {
        // Ưu tiên danh tính đã xác thực ở Gateway, không tin email client tự gửi
        String email = authEmail != null ? authEmail
                : request.userEmail() != null ? request.userEmail()
                : "demo@user.com";
        return bookingService.create(request.roomId(), request.checkIn(), request.checkOut(), email);
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingService.findAll();
    }

    @GetMapping("/user")
    public List<Booking> getBookingsByUser(
            @RequestParam String email,
            @RequestHeader(value = USER_EMAIL, required = false) String authEmail,
            @RequestHeader(value = USER_ROLE, required = false) String authRole
    ) {
        // Người dùng thường chỉ xem được booking của chính mình
        boolean isAdmin = "ADMIN".equals(authRole);
        String effectiveEmail = (authEmail != null && !isAdmin) ? authEmail : email;
        return bookingService.findByUser(effectiveEmail);
    }

    @GetMapping("/{id}")
    public Booking getBookingById(@PathVariable Long id) {
        return bookingService.findOrThrow(id);
    }

    @GetMapping("/active")
    public List<Booking> getActiveBookings() {
        return bookingService.findConfirmed();
    }

    @PutMapping("/{id}/cancel")
    public Booking cancelBooking(
            @PathVariable Long id,
            @RequestHeader(value = USER_EMAIL, required = false) String authEmail,
            @RequestHeader(value = USER_ROLE, required = false) String authRole
    ) {
        return bookingService.cancel(id, authEmail, authRole);
    }

    // ================= THANH TOÁN (gọi từ payment-service) =================

    /** Nội bộ: Gateway chặn đường này từ bên ngoài. */
    @PutMapping("/{id}/start-payment")
    public Booking startPayment(@PathVariable Long id) {
        return bookingService.startPayment(id);
    }

    /** Admin duyệt tay, hoặc payment-service gọi dự phòng khi RabbitMQ lỗi. */
    @PutMapping("/{id}/confirm")
    public Booking confirmBooking(@PathVariable Long id) {
        return bookingService.applyPaymentResult(id, true, true);
    }

    /** Nội bộ: payment-service gọi dự phòng khi RabbitMQ lỗi. */
    @PutMapping("/{id}/fail")
    public Booking failBooking(@PathVariable Long id) {
        return bookingService.applyPaymentResult(id, false, true);
    }

    // ================= ADMIN BOOKING =================

    @GetMapping("/admin")
    public List<BookingAdminResponse> getAllBookingsForAdmin() {
        // Mỗi phòng chỉ hỏi room-service một lần trong cùng một request
        Map<Long, Optional<RoomResponse>> roomCache = new HashMap<>();

        return bookingService.findAll()
                .stream()
                .map(b -> {
                    RoomResponse room = roomCache
                            .computeIfAbsent(b.getRoomId(), id -> Optional.ofNullable(roomClient.getRoom(id)))
                            .orElse(null);

                    return new BookingAdminResponse(
                            b.getId(),
                            b.getUserEmail(),
                            b.getRoomId(),
                            room != null ? room.getName() : "N/A",
                            room != null && room.getPrice() != null ? room.getPrice() : 0,
                            b.getCheckIn(),
                            b.getCheckOut(),
                            b.getStatus()
                    );
                })
                .toList();
    }

    // ================= LỖI =================

    /** Chương 6: hai tiến trình cùng sửa một booking → tiến trình ghi sau nhận 409. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> onConcurrentUpdate() {
        return Map.of("status", 409, "message", "Booking vừa được cập nhật bởi tiến trình khác, vui lòng thử lại");
    }
}
