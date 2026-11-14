package com.example.bookingservice.realtime;

import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.messaging.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Chương 4 – Thời gian thực trên nhiều instance.
 *
 * Trình duyệt giữ kết nối WebSocket tới MỘT instance booking-service, nhưng thay đổi có thể xảy ra
 * ở instance KHÁC (ví dụ instance B nhận sự kiện thanh toán). Vì vậy thay đổi được phát lên fanout
 * exchange "hotel.booking-updates"; mỗi instance có hàng đợi riêng nhận bản sao và tự đẩy tới
 * client của mình (xem BookingUpdateRelay).
 */
@Component
public class BookingUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingUpdatePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final WebSocketPusher localPusher;

    public BookingUpdatePublisher(RabbitTemplate rabbitTemplate, WebSocketPusher localPusher) {
        this.rabbitTemplate = rabbitTemplate;
        this.localPusher = localPusher;
    }

    public void publish(Booking booking) {
        if (booking == null) {
            return;
        }
        BookingUpdate update = BookingUpdate.from(booking);
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.BOOKING_UPDATES_EXCHANGE, "", update);
        } catch (AmqpException e) {
            // RabbitMQ lỗi: ít nhất vẫn báo cho client đang nối vào instance này
            log.warn("Không phát được cập nhật booking {} lên RabbitMQ ({}), chỉ đẩy cục bộ", update.id(), e.getMessage());
            localPusher.push(update);
        }
    }
}
