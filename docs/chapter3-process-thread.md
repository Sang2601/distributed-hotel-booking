# Chương 3 – Tiến trình và luồng

## 3.1. Tiến trình

Mỗi service là **một tiến trình JVM chạy trong một container riêng**. Hệ thống gồm 21 tiến trình độc lập: 10 tiến trình Spring Boot (api-gateway, room-service, booking-service mỗi loại 2 bản; discovery-server, auth, user, payment mỗi loại 1 bản), 2 PostgreSQL, RabbitMQ, Redis, 2 Nginx, db-backup và 4 tiến trình giám sát.

**Quản lý tiến trình** do Docker đảm nhiệm (vai trò tương tự PM2/Supervisor):

| Cơ chế | Cấu hình (`docker-compose.yml`) | Tác dụng |
|---|---|---|
| Tự khởi động lại | `restart: unless-stopped` | Tiến trình chết thì Docker tự chạy lại. Kiểm thử: `kill 1` (giết JVM) trong room-service → `RestartCount=1`, container chạy lại |
| Nhân bản | `deploy.replicas: 2` | Chạy nhiều tiến trình cùng một service để chia tải và dự phòng |
| Kiểm tra sống | `healthcheck` gọi `/actuator/health` mỗi 10s | Biết tiến trình còn phục vụ được không, không chỉ còn chạy |
| Thứ tự khởi động | `depends_on: condition: service_healthy` | Gateway chỉ khởi động khi các service phía sau đã sẵn sàng |
| Giới hạn tài nguyên | `mem_limit: 450m`, JVM `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` | Một service rò rỉ bộ nhớ không ảnh hưởng service khác; 21 container dùng khoảng 3 GB RAM |

Các tiến trình **giao tiếp với nhau qua mạng** (HTTP và AMQP), không dùng chung bộ nhớ.

## 3.2. Luồng trong mỗi tiến trình

| Nhóm luồng | Ở đâu | Số luồng | Việc làm |
|---|---|---|---|
| `http-nio-*-exec-*` | Mọi service Spring MVC | Tomcat thread pool (mặc định tối đa 200) | Mỗi request HTTP chạy trên một luồng |
| `reactor-http-nio-*` | api-gateway | Event loop, ≈ số nhân CPU | Xử lý bất đồng bộ, không chặn: ít luồng phục vụ được nhiều kết nối |
| `org.springframework.amqp...Container#0-*` | booking-service | 2 → 4 (`concurrency`, `max-concurrency`) | Consumer RabbitMQ xử lý sự kiện thanh toán song song |
| `clientInboundChannel-*`, `clientOutboundChannel-*` | booking-service | thread pool của WebSocket | Nhận frame STOMP từ trình duyệt, đẩy thông báo xuống |
| `redisson-netty-*` | booking-service | event loop | Giao tiếp bất đồng bộ với Redis khi giữ/nhả khoá |
| `booking-scheduler-*` | booking-service | 2 ([SchedulingConfig.java](../services/booking-service/src/main/java/com/example/bookingservice/config/SchedulingConfig.java)) | Chạy job định kỳ, tách khỏi luồng HTTP |

Log thực tế cho thấy mỗi việc chạy trên luồng riêng:

```
[nio-8084-exec-5] PaymentEventPublisher : Đã phát sự kiện payment.completed: booking=2 status=FAILED
[ntContainer#0-2] PaymentEventListener  : Nhận sự kiện thanh toán: booking=2 status=FAILED
[ing-scheduler-1] BookingExpiryJob      : Đã tự động huỷ 1 booking quá 15 phút chưa thanh toán
```

## 3.3. Background job: tự huỷ booking quá hạn

Khách đặt phòng nhưng không thanh toán sẽ giữ phòng mãi. `BookingExpiryJob` ([BookingExpiryJob.java](../services/booking-service/src/main/java/com/example/bookingservice/job/BookingExpiryJob.java)) chạy mỗi 60 giây trên luồng scheduler:

```java
@Scheduled(fixedDelayString = "${booking.expiry-check-interval-ms}", initialDelay = 30_000)
@Transactional
public void cancelExpiredBookings() {
    LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
    int cancelled = bookingRepository.cancelExpired(deadline);
    ...
}
```

- Thời hạn cấu hình qua biến môi trường `BOOKING_PAYMENT_TIMEOUT_MINUTES` (mặc định 15 phút).
- Câu lệnh là một `UPDATE ... WHERE status = 'PENDING_PAYMENT' AND created_at < ?`. Hiện có **2 instance** booking-service cùng chạy job này; vì điều kiện nằm trong câu UPDATE nguyên tử, hai instance không huỷ trùng hay huỷ nhầm booking đã chuyển trạng thái.

## 3.4. Xử lý bất đồng bộ

Luồng thanh toán được tách làm hai phần chạy trên hai tiến trình khác nhau:

1. **payment-service** (luồng HTTP): kiểm tra, ghi nhận thanh toán, phát sự kiện, trả kết quả cho khách ngay.
2. **booking-service** (luồng consumer RabbitMQ): nhận sự kiện và cập nhật trạng thái booking.

Luồng HTTP của payment-service không phải chờ booking-service xử lý xong.
