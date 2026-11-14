package com.example.bookingservice.service;

import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.entity.RoomLock;
import com.example.bookingservice.lock.DistributedRoomLock;
import com.example.bookingservice.realtime.BookingUpdatePublisher;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.repository.RoomLockRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** Các trạng thái đang giữ phòng: booking khác không được trùng ngày với chúng. */
    private static final Set<String> HOLDING_STATUSES =
            Set.of(Booking.PENDING_PAYMENT, Booking.PAYING, Booking.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final RoomLockRepository roomLockRepository;
    private final DistributedRoomLock distributedRoomLock;
    private final BookingUpdatePublisher updatePublisher;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate tx;

    public BookingService(BookingRepository bookingRepository,
                          RoomLockRepository roomLockRepository,
                          DistributedRoomLock distributedRoomLock,
                          BookingUpdatePublisher updatePublisher,
                          MeterRegistry meterRegistry,
                          PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.roomLockRepository = roomLockRepository;
        this.distributedRoomLock = distributedRoomLock;
        this.updatePublisher = updatePublisher;
        this.meterRegistry = meterRegistry;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ================= TẠO BOOKING (CHƯƠNG 6) =================

    public Booking create(Long roomId, LocalDate checkIn, LocalDate checkOut, String userEmail) {
        if (roomId == null || checkIn == null || checkOut == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu phòng hoặc ngày check-in/check-out");
        }
        if (!checkIn.isBefore(checkOut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày check-out phải sau ngày check-in");
        }

        // 1. Khoá phân tán Redis, dùng chung cho mọi instance. Redis lỗi → Optional.empty()
        Optional<RLock> redisLock = distributedRoomLock.acquire(roomId);
        boolean useDatabaseLock = redisLock.isEmpty();
        if (useDatabaseLock) {
            ensureLockRow(roomId);
        }

        try {
            Booking created = tx.execute(status -> {
                if (useDatabaseLock) {
                    // 1b. Dự phòng khi Redis lỗi: khoá dòng của phòng trong PostgreSQL (SELECT ... FOR UPDATE)
                    roomLockRepository.lockByRoomId(roomId)
                            .orElseThrow(() -> new IllegalStateException("Thiếu dòng khoá cho phòng " + roomId));
                    meterRegistry.counter("booking.room.lock", "type", "database").increment();
                }
                return insertIfFree(roomId, checkIn, checkOut, userEmail);
            });
            updatePublisher.publish(created);
            return created;
        } finally {
            // 4. Nhả khoá Redis SAU khi transaction đã commit
            redisLock.ifPresent(distributedRoomLock::release);
        }
    }

    /** Chỉ gọi khi đang giữ khoá phòng (Redis hoặc database). */
    private Booking insertIfFree(Long roomId, LocalDate checkIn, LocalDate checkOut, String userEmail) {
        // 2. Đang giữ khoá nên kết quả kiểm tra không thể bị request khác làm sai lệch
        if (bookingRepository.existsOverlap(roomId, checkIn, checkOut, HOLDING_STATUSES)) {
            log.info("Từ chối đặt phòng {} ({} → {}) cho {}: trùng lịch", roomId, checkIn, checkOut, userEmail);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng đã có người đặt trong khoảng ngày này");
        }

        // 3. Ghi booking
        Booking booking = new Booking();
        booking.setRoomId(roomId);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkOut);
        booking.setUserEmail(userEmail);
        booking.setStatus(Booking.PENDING_PAYMENT);
        return bookingRepository.save(booking);
    }

    /** Tạo dòng khoá database cho phòng nếu chưa có. Hai request cùng tạo thì một bên lỗi khoá chính, bỏ qua được. */
    private void ensureLockRow(Long roomId) {
        if (roomLockRepository.existsById(roomId)) {
            return;
        }
        try {
            tx.executeWithoutResult(s -> roomLockRepository.saveAndFlush(new RoomLock(roomId)));
        } catch (DataIntegrityViolationException e) {
            // Request khác vừa tạo xong dòng khoá này – không sao
        }
    }

    // ================= THANH TOÁN (CHƯƠNG 6) =================

    /**
     * Payment-service gọi trước khi trừ tiền. Chuyển PENDING_PAYMENT → PAYING một cách nguyên tử,
     * nên hai lần bấm thanh toán đồng thời chỉ có một lần được đi tiếp.
     */
    public Booking startPayment(Long id) {
        Booking booking = tx.execute(status -> {
            int updated = bookingRepository.compareAndSetStatus(id, Booking.PENDING_PAYMENT, Booking.PAYING);
            Booking current = findOrThrow(id);
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Booking không ở trạng thái chờ thanh toán (hiện tại: " + current.getStatus() + ")");
            }
            return current;
        });
        updatePublisher.publish(booking);
        return booking;
    }

    /**
     * Ghi nhận kết quả thanh toán. Idempotent: nhận cùng một kết quả nhiều lần (RabbitMQ gửi lại,
     * hoặc fallback REST sau khi message đã tới) thì lần sau không làm gì.
     *
     * @param strict true khi gọi qua HTTP (trả lỗi 409 nếu trạng thái không hợp lệ);
     *               false khi xử lý message (chỉ ghi log, không ném lỗi để tránh gửi lại vô ích)
     */
    public Booking applyPaymentResult(Long id, boolean success, boolean strict) {
        String target = success ? Booking.CONFIRMED : Booking.PAYMENT_FAILED;
        boolean[] changed = {false};
        Booking result = tx.execute(status -> {
            Booking booking = bookingRepository.findById(id).orElse(null);
            if (booking == null) {
                if (strict) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
                }
                log.warn("Bỏ qua kết quả thanh toán cho booking {} không tồn tại", id);
                return null;
            }
            if (target.equals(booking.getStatus())) {
                return booking; // đã xử lý rồi
            }
            boolean canTransition = Booking.PAYING.equals(booking.getStatus())
                    || Booking.PENDING_PAYMENT.equals(booking.getStatus());
            if (!canTransition) {
                if (strict) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Không thể chuyển booking từ " + booking.getStatus() + " sang " + target);
                }
                log.warn("Bỏ qua kết quả thanh toán {} cho booking {} đang ở trạng thái {}",
                        target, id, booking.getStatus());
                return booking;
            }
            booking.setStatus(target);
            changed[0] = true;
            return bookingRepository.save(booking); // @Version chặn ghi đè đồng thời
        });
        if (changed[0]) {
            updatePublisher.publish(result);
        }
        return result;
    }

    // ================= HUỶ =================

    public Booking cancel(Long id, String requesterEmail, String requesterRole) {
        boolean[] changed = {false};
        Booking result = tx.execute(status -> {
            Booking booking = findOrThrow(id);
            boolean isAdmin = "ADMIN".equals(requesterRole);
            if (requesterEmail != null && !isAdmin && !requesterEmail.equals(booking.getUserEmail())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không thể huỷ booking của người khác");
            }
            if (Booking.CANCELLED.equals(booking.getStatus())) {
                return booking;
            }
            if (!Booking.PENDING_PAYMENT.equals(booking.getStatus())
                    && !Booking.PAYMENT_FAILED.equals(booking.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Không thể huỷ booking đang ở trạng thái " + booking.getStatus());
            }
            booking.setStatus(Booking.CANCELLED);
            changed[0] = true;
            return bookingRepository.save(booking);
        });
        if (changed[0]) {
            updatePublisher.publish(result);
        }
        return result;
    }

    // ================= ĐỌC =================

    public Booking findOrThrow(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    public List<Booking> findByUser(String email) {
        return bookingRepository.findByUserEmail(email);
    }

    public List<Booking> findConfirmed() {
        return bookingRepository.findByStatus(Booking.CONFIRMED);
    }
}
