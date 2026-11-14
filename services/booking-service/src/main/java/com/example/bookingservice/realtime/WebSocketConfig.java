package com.example.bookingservice.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Chương 4 – WebSocket (STOMP).
 * Trình duyệt kết nối ws://<gateway>/ws?access_token=<JWT>. Gateway kiểm tra token rồi gắn
 * X-User-Email / X-User-Role vào yêu cầu bắt tay; ở đây lưu lại để kiểm tra quyền đăng ký kênh:
 *   /topic/admin.bookings      – chỉ ADMIN
 *   /topic/user.{email}        – chỉ đúng người dùng đó (Chương 5)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String EMAIL = "userEmail";
    private static final String ROLE = "userRole";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        String email = request.getHeaders().getFirst("X-User-Email");
                        String role = request.getHeaders().getFirst("X-User-Role");
                        if (email == null) {
                            return false; // không đi qua Gateway / chưa đăng nhập
                        }
                        attributes.put(EMAIL, email);
                        attributes.put(ROLE, role);
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                    }
                });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    Map<String, Object> session = accessor.getSessionAttributes();
                    String email = session == null ? null : (String) session.get(EMAIL);
                    String role = session == null ? null : (String) session.get(ROLE);
                    String destination = accessor.getDestination();

                    boolean allowed = destination != null && (
                            (WebSocketPusher.ADMIN_TOPIC.equals(destination) && "ADMIN".equals(role))
                            || destination.equals(WebSocketPusher.USER_TOPIC_PREFIX + email));
                    if (!allowed) {
                        throw new MessagingException("Không có quyền đăng ký kênh " + destination);
                    }
                }
                return message;
            }
        });
    }
}
