# Chương 6 – Đồng bộ hoá

## 6.1. Vấn đề: đặt trùng phòng

Phiên bản ban đầu lưu booking mà không kiểm tra lịch. Nếu chỉ thêm bước "kiểm tra rồi lưu", vẫn xảy ra **race condition**:

```
Request A: kiểm tra phòng 5 ngày 1–4 → trống
Request B: kiểm tra phòng 5 ngày 1–4 → trống
Request A: lưu booking
Request B: lưu booking   ← trùng phòng
```

Hệ thống chạy **2 instance booking-service**, nên A và B có thể nằm trên hai tiến trình (thậm chí hai máy) khác nhau. Khoá trong bộ nhớ (`synchronized`) không giải quyết được; cần **khoá phân tán**.

## 6.2. Giải pháp 1 – Khoá phân tán Redis (Redlock)

[DistributedRoomLock.java](../services/booking-service/src/main/java/com/example/bookingservice/lock/DistributedRoomLock.java) dùng Redisson `RLock` trên khoá `lock:room:{id}` trong Redis dùng chung:

```java
Optional<RLock> redisLock = distributedRoomLock.acquire(roomId);   // tryLock(chờ 5s, hết hạn 10s)
try {
    Booking created = tx.execute(s -> insertIfFree(roomId, checkIn, checkOut, email)); // kiểm tra trùng + lưu
    ...
} finally {
    redisLock.ifPresent(distributedRoomLock::release);              // nhả SAU khi commit
}
```

| Thiết lập | Giá trị | Lý do |
|---|---|---|
| Phạm vi khoá | Theo từng phòng | Đặt phòng khác nhau vẫn chạy song song |
| Chờ tối đa | 5 giây | Quá lâu thì trả 409 để khách thử lại |
| Thời hạn (lease) | 10 giây | Instance chết khi đang giữ khoá cũng không làm kẹt phòng mãi |
| Nhả khoá | Sau khi transaction commit | Request sau chắc chắn thấy booking vừa lưu |

Điều kiện trùng: hai khoảng `[a, b)` và `[c, d)` giao nhau khi `a < d` và `c < b`. Ngày trả phòng của khách trước bằng ngày nhận phòng của khách sau thì **không** tính là trùng.

```mermaid
sequenceDiagram
    participant A as Request A<br/>(booking #1)
    participant R as Redis
    participant DB as PostgreSQL
    participant B as Request B<br/>(booking #2)
    A->>R: tryLock lock:room:5 → OK
    B->>R: tryLock lock:room:5 → chờ...
    A->>DB: kiểm tra trùng → không; INSERT; COMMIT
    A->>R: unlock
    R-->>B: được khoá
    B->>DB: kiểm tra trùng → CÓ
    B->>R: unlock
    B-->>B: 409 Conflict
```

**Kết quả kiểm thử** – 20 request đồng thời (2 tài khoản) cùng đặt phòng 5, ngày 01–04/12, rơi vào **cả hai** instance:

```
1 × 200 OK, 19 × 409 Conflict
booking-service-1 xử lý 9 request, booking-service-2 xử lý 10 request
booking_room_lock_total{type="redis"} = 10 + 10
```

## 6.3. Giải pháp 2 – Pessimistic locking database (dự phòng khi Redis lỗi)

Nếu Redis không phản hồi trong 0,5 giây, booking-service chuyển sang khoá dòng của phòng trong bảng `room_lock` của PostgreSQL:

```sql
SELECT ... FROM room_lock WHERE room_id = ? FOR UPDATE   -- giữ tới khi transaction kết thúc
```

Khoá này cũng phân tán (nằm ở database dùng chung cho cả hai instance), chỉ chậm hơn và dồn tải vào database. Sau một lần Redis lỗi, service bỏ qua Redis trong 10 giây để không request nào phải chờ timeout.

**Kết quả kiểm thử** – tắt Redis, 10 request đồng thời đặt phòng 8: `1 × 200, 9 × 409`, metric `booking_room_lock_total{type="database"}` tăng. Bật lại Redis, lần đặt tiếp theo quay về `type="redis"`.

| | Redis lock | Database lock |
|---|---|---|
| Độc lập với database | ✅ | ❌ |
| Tốc độ | Nhanh, không giữ kết nối DB khi chờ | Request chờ chiếm kết nối DB |
| Phụ thuộc | Redis | PostgreSQL (vốn đã bắt buộc) |
| Vai trò | Chính | Dự phòng |

## 6.4. Giải pháp 3 – Compare-and-set khi thanh toán

Khách bấm "thanh toán" hai lần liên tiếp có thể bị trừ tiền hai lần. payment-service phải **giữ quyền thanh toán** trước khi thu tiền:

```sql
UPDATE booking SET status = 'PAYING', version = version + 1
WHERE id = ? AND status = 'PENDING_PAYMENT'
```

Câu `UPDATE` nguyên tử: chỉ request đầu tiên nhận "1 dòng"; request sau nhận "0 dòng" và bị trả 409.

**Kết quả kiểm thử** – 2 request thanh toán đồng thời: `1 × 200`, `1 × 409`, bảng `payment` chỉ có **1** bản ghi.

## 6.5. Giải pháp 4 – Optimistic locking

Entity `Booking` có `@Version`. Mỗi lần cập nhật, Hibernate sinh `UPDATE ... WHERE id = ? AND version = ?`. Nếu admin bấm "Duyệt" đúng lúc sự kiện thanh toán tới, bên ghi sau nhận lỗi thay vì ghi đè âm thầm; API trả 409.

## 6.6. Giải pháp 5 – Đồng bộ qua Message Queue: consumer idempotent

RabbitMQ đảm bảo **at-least-once**: một message có thể tới hơn một lần. Với 2 instance booking-service cùng nghe một queue (competing consumers), mỗi message chỉ giao cho một instance, nhưng vẫn có thể giao lại nếu instance đó chết trước khi xác nhận. booking-service xử lý idempotent:

- Booking đã ở trạng thái đích (ví dụ đã `CONFIRMED`) → bỏ qua.
- Booking ở trạng thái không hợp lệ (ví dụ đã `CANCELLED`) → ghi log, không ném lỗi.

## 6.7. Tổng kết

| Tình huống | Cơ chế | Loại |
|---|---|---|
| Nhiều người đặt cùng phòng | Redis Redlock (chính), `SELECT FOR UPDATE` (dự phòng) | Distributed / pessimistic lock |
| Bấm thanh toán 2 lần | `UPDATE ... WHERE status = ?` | Compare-and-set |
| Admin và sự kiện cùng sửa booking | `@Version` | Optimistic lock |
| Message tới trùng | Consumer idempotent | Đồng bộ qua message queue |
| 2 instance cùng chạy job huỷ quá hạn | `UPDATE` có điều kiện | Nguyên tử ở database |
