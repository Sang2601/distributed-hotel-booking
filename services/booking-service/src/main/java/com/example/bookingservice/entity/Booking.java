package com.example.bookingservice.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Booking {

    /*
     * Vòng đời trạng thái:
     *   PENDING_PAYMENT ──(payment-service bắt đầu thanh toán)──> PAYING
     *   PAYING ──(sự kiện payment SUCCESS)──> CONFIRMED
     *   PAYING ──(sự kiện payment FAILED)──> PAYMENT_FAILED
     *   PENDING_PAYMENT ──(quá hạn / người dùng huỷ)──> CANCELLED
     */
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAYING = "PAYING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roomId;

    private String userEmail;

    private LocalDate checkIn;

    private LocalDate checkOut;

    private String status;

    private LocalDateTime createdAt;

    /**
     * Chương 6 – Optimistic locking: mỗi lần cập nhật, Hibernate kiểm tra version.
     * Nếu hai tiến trình cùng sửa một booking, tiến trình ghi sau sẽ bị từ chối
     * (ObjectOptimisticLockingFailureException) thay vì ghi đè âm thầm.
     */
    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ===== GETTERS & SETTERS =====

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(LocalDate checkIn) {
        this.checkIn = checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDate checkOut) {
        this.checkOut = checkOut;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
