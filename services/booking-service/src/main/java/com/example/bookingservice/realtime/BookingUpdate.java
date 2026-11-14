package com.example.bookingservice.realtime;

import com.example.bookingservice.entity.Booking;

import java.time.LocalDate;

/** Thông báo gửi tới trình duyệt qua WebSocket mỗi khi booking đổi trạng thái. */
public record BookingUpdate(
        Long id,
        Long roomId,
        String userEmail,
        String status,
        LocalDate checkIn,
        LocalDate checkOut
) {
    public static BookingUpdate from(Booking b) {
        return new BookingUpdate(b.getId(), b.getRoomId(), b.getUserEmail(), b.getStatus(), b.getCheckIn(), b.getCheckOut());
    }
}
