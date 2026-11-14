package com.example.paymentservice.messaging;

import com.example.paymentservice.client.BookingClient;
import com.example.paymentservice.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Chương 2, 4 – Payment-service không gọi trực tiếp booking-service để xác nhận nữa,
 * mà phát sự kiện "payment.completed". Booking-service tự lắng nghe và cập nhật.
 * Hai service không phụ thuộc thời gian: booking-service tạm tắt thì message nằm chờ trong queue.
 *
 * Chương 8 – Nếu chính RabbitMQ không gửi được, chuyển sang gọi REST trực tiếp (fallback).
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final BookingClient bookingClient;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate, BookingClient bookingClient) {
        this.rabbitTemplate = rabbitTemplate;
        this.bookingClient = bookingClient;
    }

    public void publishCompleted(Payment payment) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                payment.getId(), payment.getBookingId(), payment.getStatus(), payment.getAmount());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.PAYMENT_COMPLETED_KEY, event);
            log.info("Đã phát sự kiện payment.completed: booking={} status={}",
                    event.bookingId(), event.status());
        } catch (AmqpException e) {
            log.warn("Không gửi được sự kiện lên RabbitMQ ({}), chuyển sang gọi REST trực tiếp", e.getMessage());
            try {
                bookingClient.applyPaymentResult(payment.getBookingId(), "SUCCESS".equals(payment.getStatus()));
            } catch (RuntimeException restError) {
                log.error("Fallback REST cũng thất bại, booking {} cần xử lý thủ công: {}",
                        payment.getBookingId(), restError.toString());
            }
        }
    }
}
