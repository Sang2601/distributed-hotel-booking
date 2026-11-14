# Kịch bản demo khi thuyết trình

Chạy lệnh trong PowerShell tại thư mục gốc dự án. Mở sẵn Docker Desktop. Tổng thời gian khoảng 15 phút.

## 0. Chuẩn bị (trước buổi demo ~5 phút)

```powershell
docker compose up -d --build
docker compose ps          # chờ tất cả (healthy), ~2 phút
```

Mở sẵn các tab:

| Tab | Địa chỉ |
|---|---|
| Web khách | http://localhost:5500 |
| Web admin | http://localhost:5500/admin-login.html (`23010139@gmail.com` / `nht123456`) |
| Eureka | http://localhost:8761 |
| Grafana | http://localhost:3000 (admin / admin) – dashboard tự mở |
| RabbitMQ | http://localhost:15672 (guest / guest) |

Tạo tài khoản và lấy token:

```powershell
$G = "http://localhost:8088/api"
Invoke-RestMethod -Method Post "$G/users" -ContentType "application/json" -Body '{"username":"alice","email":"alice@test.com","password":"123456"}'
$T = (Invoke-RestMethod -Method Post "$G/auth/login" -ContentType "application/json" -Body '{"email":"alice@test.com","password":"123456"}').token
$H = @{ Authorization = "Bearer $T" }
```

## 1. Kiến trúc + Service Registry (C1, C2, C5) – 2 phút

- `docker compose ps`: 21 container; gateway, room, booking mỗi loại 2 bản.
- Tab **Eureka**: mỗi service đã tự đăng ký kèm IP. Giải thích: không còn ghi cứng `localhost:8083`, gọi bằng tên `room-service`.
- Mở sơ đồ trong [chapter2-architecture.md](chapter2-architecture.md).

## 2. Cân bằng tải (C8) – 1 phút

```powershell
1..6 | ForEach-Object {
  $r = Invoke-WebRequest "$G/rooms/1" -UseBasicParsing
  "{0}   gateway={1}" -f $r.Headers["X-Served-By"], $r.Headers["X-Gateway-Upstream"]
}
```

Hai cột luân phiên giữa 2 instance. Trên Grafana, panel "Request/giây theo TỪNG instance" có các đường gần bằng nhau.

## 3. Định danh (C5) – 1 phút

```powershell
Invoke-WebRequest "$G/bookings/active" -UseBasicParsing          # 401: chưa đăng nhập
Invoke-WebRequest "$G/users" -Headers $H -UseBasicParsing        # 403: không phải admin
Invoke-RestMethod "$G/bookings/active" -Headers $H               # 200
```

## 4. Đồng bộ hoá – khoá phân tán (C6) – 2 phút

20 request đồng thời đặt cùng phòng, rơi vào 2 instance booking-service khác nhau:

```powershell
$jobs = 1..20 | ForEach-Object {
  Start-Job -ArgumentList $G, $T -ScriptBlock {
    param($G, $T)
    try {
      Invoke-RestMethod -Method Post "$G/bookings" -Headers @{Authorization="Bearer $T"} `
        -ContentType "application/json" -Body '{"roomId":12,"checkIn":"2026-12-24","checkOut":"2026-12-26"}' | Out-Null
      "200 OK"
    } catch { [int]$_.Exception.Response.StatusCode }
  }
}
$jobs | Wait-Job | Receive-Job | Group-Object | Select-Object Count, Name
$jobs | Remove-Job
docker compose logs booking-service | Select-String "trùng lịch" | Group-Object { $_.Line.Split(" ")[0] } | Select-Object Count, Name
```

Kết quả: `1 × 200`, `19 × 409`, request chia cho cả hai instance. Grafana: panel "Khoá đặt phòng theo loại" tăng ở `redis`.

## 5. Thời gian thực + sự kiện + cache (C2, C3, C4) – 3 phút

1. Tab web khách: đăng nhập alice → **Lịch sử**. Góc trái dưới hiện **● Cập nhật trực tiếp**.
2. Tab admin: đăng nhập admin, cũng có nhãn trực tiếp.
3. Ở một tab khách khác: đặt phòng, rồi thanh toán.
4. Không cần tải lại trang: tab Lịch sử và tab admin hiện thông báo "Booking #… đã thanh toán ✅".
5. Chỉ ra luồng: payment-service → RabbitMQ → booking-service → fanout → WebSocket:

```powershell
docker compose logs payment-service booking-service | Select-String "sự kiện"
```

Cache:

```powershell
docker compose exec redis redis-cli FLUSHALL
1..4 | ForEach-Object { (Measure-Command { Invoke-RestMethod "$G/rooms" | Out-Null }).TotalMilliseconds }
docker compose exec redis redis-cli KEYS "*"
```

Lần đầu chậm (truy vấn DB), các lần sau nhanh (Redis).

## 6. Chịu lỗi (C8) – 3 phút

Để Grafana mở bên cạnh.

```powershell
# a) Giết một instance – request vẫn thành công
docker kill hotel-booking-room-service-1
1..20 | ForEach-Object { (Invoke-WebRequest "$G/rooms/2" -UseBasicParsing).StatusCode } | Group-Object
# Eureka/Grafana: room-service còn 1 instance sau ~15 giây

# b) Tiến trình crash → Docker tự khởi động lại
docker compose up -d
docker compose exec --index 2 room-service kill 1
Start-Sleep 5; docker inspect -f "{{.RestartCount}}" hotel-booking-room-service-2

# c) Redis chết – đặt phòng chuyển sang khoá database, xem phòng đọc thẳng DB
docker compose stop redis
Invoke-RestMethod -Method Post "$G/bookings" -Headers $H -ContentType "application/json" -Body '{"roomId":13,"checkIn":"2027-01-01","checkOut":"2027-01-02"}'
docker compose logs booking-service | Select-String "khoá database"
docker compose start redis

# d) Room-service chết hẳn – circuit breaker mở, trang admin vẫn chạy (tên phòng "N/A")
docker compose stop room-service
# Admin → Quản lý booking; Grafana: panel Circuit breaker chuyển OPEN
docker compose start room-service
```

Log tập trung: Grafana → Explore → Loki → `{service=~".+"} | level="WARN"`.

## 7. Replication + sao lưu + khôi phục (C7) – 2 phút

```powershell
# Replica đang đồng bộ, chỉ đọc
docker compose exec postgres psql -U hotel -d postgres -c "select client_addr, state from pg_stat_replication"
docker compose exec postgres-replica psql -U hotel -d bookingdb -c "select count(*) from booking"
docker compose exec postgres-replica psql -U hotel -d bookingdb -c "delete from booking"   # bị từ chối

# Sao lưu (từ replica)
docker compose exec db-backup sh /scripts/backup-once.sh
ls backups

# "Sự cố": xoá nhầm trên primary → replica cũng mất theo
docker compose exec postgres psql -U hotel -d bookingdb -c "DELETE FROM booking"
docker compose exec postgres-replica psql -U hotel -d bookingdb -c "select count(*) from booking"

# Khôi phục vào primary
docker compose stop booking-service
docker compose exec db-backup sh /scripts/restore.sh <tên-thư-mục-vừa-tạo> bookingdb
docker compose start booking-service
```

## Dọn dẹp

```powershell
docker compose down        # giữ dữ liệu
docker compose down -v     # xoá cả dữ liệu
```
