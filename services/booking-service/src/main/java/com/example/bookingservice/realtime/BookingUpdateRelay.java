package com.example.bookingservice.realtime;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Nhận bản sao mọi thay đổi booking (từ bất kỳ instance nào) và đẩy xuống WebSocket. */
@Component
public class BookingUpdateRelay {

    private final WebSocketPusher pusher;

    public BookingUpdateRelay(WebSocketPusher pusher) {
        this.pusher = pusher;
    }

    @RabbitListener(queues = "#{bookingUpdatesQueue.name}")
    public void onBookingUpdate(BookingUpdate update) {
        pusher.push(update);
    }
}
