package com.example.bookingservice.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Đẩy thông báo tới các trình duyệt đang kết nối WebSocket vào CHÍNH instance này. */
@Component
public class WebSocketPusher {

    public static final String ADMIN_TOPIC = "/topic/admin.bookings";
    public static final String USER_TOPIC_PREFIX = "/topic/user.";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPusher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void push(BookingUpdate update) {
        messagingTemplate.convertAndSend(ADMIN_TOPIC, update);
        if (update.userEmail() != null) {
            messagingTemplate.convertAndSend(USER_TOPIC_PREFIX + update.userEmail(), update);
        }
    }
}
