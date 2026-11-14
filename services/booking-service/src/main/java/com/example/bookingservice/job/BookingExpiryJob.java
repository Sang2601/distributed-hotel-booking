package com.example.bookingservice.job;

import com.example.bookingservice.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Chương 3 – Background job.
 * Chạy định kỳ trên luồng scheduler riêng (booking-scheduler-*), tách khỏi luồng xử lý HTTP.
 * Huỷ các booking giữ phòng mà không thanh toán quá thời hạn, để trả phòng cho khách khác.
 * Câu UPDATE chỉ đụng các dòng còn PENDING_PAYMENT nên chạy trên nhiều instance cùng lúc vẫn an toàn.
 */
@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingRepository bookingRepository;
    private final long timeoutMinutes;

    public BookingExpiryJob(BookingRepository bookingRepository,
                            @Value("${booking.payment-timeout-minutes}") long timeoutMinutes) {
        this.bookingRepository = bookingRepository;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedDelayString = "${booking.expiry-check-interval-ms}", initialDelay = 30_000)
    @Transactional
    public void cancelExpiredBookings() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        int cancelled = bookingRepository.cancelExpired(deadline);
        if (cancelled > 0) {
            log.info("[{}] Đã tự động huỷ {} booking quá {} phút chưa thanh toán",
                    Thread.currentThread().getName(), cancelled, timeoutMinutes);
        }
    }
}
