# Distributed Hotel Booking

Hệ thống đặt phòng khách sạn theo kiến trúc phân tán: 7 service Spring Boot (gateway, room, booking chạy 2 instance), Eureka service registry, Nginx load balancer, RabbitMQ, Redis (cache + khoá phân tán), WebSocket, PostgreSQL primary/replica với sao lưu tự động, giám sát bằng Prometheus + Grafana + Loki.

## Chạy hệ thống

Yêu cầu: Docker Desktop, khoảng 4 GB RAM trống. Không cần cài Java hay Maven, việc build diễn ra trong container.

```powershell
docker compose up -d --build
docker compose ps        # chờ tất cả service ở trạng thái (healthy), khoảng 2 phút
```

| Địa chỉ | Nội dung |
|---|---|
| http://localhost:5500 | Trang khách: đăng ký, đăng nhập, đặt phòng, thanh toán, lịch sử (cập nhật trực tiếp) |
| http://localhost:5500/admin-login.html | Trang quản trị (`23010139@gmail.com` / `nht123456`) |
| http://localhost:8088/api | API, qua Nginx load balancer → 2 api-gateway |
| http://localhost:8761 | Eureka: danh sách instance đang chạy |
| http://localhost:3000 | Grafana: dashboard giám sát + log (admin / admin) |
| http://localhost:9090 | Prometheus |
| http://localhost:15672 | RabbitMQ Management (guest / guest) |

Dừng: `docker compose down` (giữ dữ liệu) hoặc `docker compose down -v` (xoá dữ liệu).

## Kiến trúc

```mermaid
flowchart LR
    U[frontend :5500] -->|HTTP / WS + JWT| LB[Nginx LB :8088]
    LB --> GW[api-gateway ×2]
    GW -->|lb://| AUTH[auth] & USER[user] & ROOM[room ×2] & BOOK[booking ×2] & PAY[payment]
    AUTH --> USER
    BOOK --> ROOM
    PAY --> BOOK & ROOM
    PAY -- payment.completed --> MQ[(RabbitMQ)] --> BOOK
    ROOM & BOOK --- RD[(Redis)]
    USER & ROOM & BOOK & PAY --- PG[(PostgreSQL primary)] --> PGR[(replica)]
    BK[db-backup] -.-> PGR
    EU[(Eureka)] -.- GW & ROOM & BOOK
    PROM[Prometheus + Grafana + Loki] -.-> EU
```

## Tài liệu theo bảng chấm điểm

| Chương | Nội dung chính | Tài liệu |
|---|---|---|
| 1. Mở đầu | Bài toán, mục tiêu, yêu cầu, sơ đồ tổng thể | [chapter1](docs/chapter1-introduction.md) |
| 2. Kiến trúc | Microservices + event-driven, 21 node, sơ đồ tuần tự/triển khai | [chapter2](docs/chapter2-architecture.md) |
| 3. Tiến trình & luồng | Container, replicas, restart, thread pool, consumer threads, `@Scheduled` | [chapter3](docs/chapter3-process-thread.md) |
| 4. Trao đổi thông tin | REST, RabbitMQ (topic + fanout, DLQ), WebSocket, Redis cache | [chapter4](docs/chapter4-communication.md) |
| 5. Định danh | Docker DNS, **Eureka service registry**, JWT, phân quyền, URI | [chapter5](docs/chapter5-identification.md) |
| 6. Đồng bộ hoá | **Redis Redlock**, khoá database dự phòng, compare-and-set, `@Version`, consumer idempotent | [chapter6](docs/chapter6-synchronization.md) |
| 7. Sao lưu | **PostgreSQL replication**, `pg_dump` tự động từ replica, khôi phục | [chapter7](docs/chapter7-backup.md) |
| 8. Chịu lỗi | **Nginx + Eureka load balancing/failover**, circuit breaker, retry, **Prometheus/Grafana**, **Loki** | [chapter8](docs/chapter8-fault-tolerance.md) |
| Thuyết trình | Kịch bản demo từng chương | [demo](docs/demo.md) |

## Chạy một service riêng khi phát triển (không Docker)

Mỗi service vẫn chạy được với H2 in-memory (cần Java 17+ và Maven), nhưng cần discovery-server chạy trước:

```bash
cd services/discovery-server && mvn spring-boot:run
cd services/room-service && mvn spring-boot:run
```

Thiếu Redis/RabbitMQ thì service vẫn chạy, chỉ dùng phương án dự phòng (đọc thẳng DB, khoá database, gọi REST).

## Cấu hình qua biến môi trường

Tạo file `.env` cạnh `docker-compose.yml` để ghi đè:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `hotel` / `hotel123` | Tài khoản PostgreSQL |
| `REPLICATION_PASSWORD` | `replica123` | Mật khẩu user replicator |
| `JWT_SECRET` | (chuỗi mẫu) | Khoá ký JWT, dùng chung auth-service và api-gateway |
| `GRAFANA_PASSWORD` | `admin` | Mật khẩu admin Grafana |
| `BOOKING_PAYMENT_TIMEOUT_MINUTES` | `15` | Booking chưa thanh toán sau bao lâu thì tự huỷ |
| `BACKUP_INTERVAL_SECONDS` | `3600` | Chu kỳ sao lưu |
| `BACKUP_KEEP` | `24` | Số bản sao lưu giữ lại |
