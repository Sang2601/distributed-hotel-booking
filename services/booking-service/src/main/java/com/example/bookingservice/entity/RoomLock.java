package com.example.bookingservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Chương 6 – Pessimistic locking.
 * Mỗi phòng có đúng một dòng trong bảng này. Khi đặt phòng, transaction khoá dòng
 * của phòng đó bằng SELECT ... FOR UPDATE, nên các request đặt CÙNG một phòng phải
 * xếp hàng lần lượt (kể cả khi chạy nhiều instance booking-service, vì khoá nằm ở
 * database dùng chung). Request đặt phòng KHÁC không bị ảnh hưởng.
 */
@Entity
@Table(name = "room_lock")
public class RoomLock {

    @Id
    private Long roomId;

    protected RoomLock() {
    }

    public RoomLock(Long roomId) {
        this.roomId = roomId;
    }

    public Long getRoomId() {
        return roomId;
    }
}
