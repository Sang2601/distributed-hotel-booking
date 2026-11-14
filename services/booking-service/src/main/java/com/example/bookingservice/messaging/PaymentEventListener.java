package com.example.bookingservice.messaging;

import com.example.bookingservice.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final BookingService bookingService;

    public PaymentEventListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Chạy trên các luồng consumer của RabbitMQ (2–4 luồng, xem application.properties).
     * Việc cập nhật là idempotent nên RabbitMQ có gửi lại cùng message cũng không sao.
     */
    @RabbitListener(queues = RabbitConfig.PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("[{}] Nhận sự kiện thanh toán: booking={} status={}",
                Thread.currentThread().getName(), event.bookingId(), event.status());
        bookingService.applyPaymentResult(event.bookingId(), event.isSuccess(), false);
    }
}
