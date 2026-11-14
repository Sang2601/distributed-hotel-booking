package com.example.bookingservice.repository;

import com.example.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserEmail(String userEmail);

    List<Booking> findByStatus(String status);

    /**
     * Có booking nào của phòng này đang giữ chỗ và trùng khoảng ngày không?
     * Hai khoảng [a, b) và [c, d) giao nhau khi a < d và c < b
     * (ngày check-out của khách trước = ngày check-in của khách sau thì không tính là trùng).
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.roomId = :roomId
          AND b.status IN :activeStatuses
          AND b.checkIn < :checkOut
          AND b.checkOut > :checkIn
    """)
    boolean existsOverlap(@Param("roomId") Long roomId,
                          @Param("checkIn") LocalDate checkIn,
                          @Param("checkOut") LocalDate checkOut,
                          @Param("activeStatuses") Collection<String> activeStatuses);

    /**
     * Compare-and-set: chỉ đổi trạng thái khi trạng thái hiện tại đúng như mong đợi.
     * Câu UPDATE là nguyên tử ở database, nên khi hai request thanh toán cùng một booking
     * đến đồng thời, chỉ một request nhận được kết quả 1 dòng; request còn lại nhận 0.
     */
    @Modifying
    @Query("""
        UPDATE Booking b SET b.status = :newStatus, b.version = b.version + 1
        WHERE b.id = :id AND b.status = :expectedStatus
    """)
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("newStatus") String newStatus);

    /** Huỷ hàng loạt booking chưa thanh toán đã quá hạn. */
    @Modifying
    @Query("""
        UPDATE Booking b SET b.status = 'CANCELLED', b.version = b.version + 1
        WHERE b.status = 'PENDING_PAYMENT' AND b.createdAt < :deadline
    """)
    int cancelExpired(@Param("deadline") LocalDateTime deadline);
}
