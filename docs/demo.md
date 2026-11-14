# Kịch bản demo khi thuyết trình

Chạy các lệnh trong PowerShell, tại thư mục gốc dự án. Mở sẵn Docker Desktop.

## 0. Khởi động (trước buổi demo ~3 phút)

```powershell
docker compose up -d --build
docker compose ps          # chờ tất cả (healthy)
```

- Web: http://localhost:5500
- Admin: http://localhost:5500/admin-login.html (`23010139@gmail.com` / `nht123456`)
- RabbitMQ: http://localhost:15672 (guest / guest)

Lấy token để gọi API:

```powershell
$G = "http://localhost:8088/api"
Invoke-RestMethod -Method Post "$G/users" -ContentType "application/json" -Body '{"username":"alice","email":"alice@test.com","password":"123456"}'
$T = (Invoke-RestMethod -Method Post "$G/auth/login" -ContentType "application/json" -Body '{"email":"alice@test.com","password":"123456"}').token
$H = @{ Authorization = "Bearer $T" }
```

## 1. Kiến trúc (C1, C2) – 1 phút

Mở `docker compose ps`: 10 container, mỗi service một tiến trình. Mở sơ đồ trong [chapter2-architecture.md](chapter2-architecture.md).

## 2. Định danh (C5) – 1 phút

```powershell
Invoke-WebRequest "$G/bookings/active"                  # 401: chưa đăng nhập
Invoke-WebRequest "$G/users" -Headers $H                # 403: không phải admin
Invoke-RestMethod "$G/bookings/active" -Headers $H      # 200
```

## 3. Đồng bộ hoá – chống đặt trùng (C6) – 2 phút

10 request đồng thời đặt cùng phòng, cùng ngày:

```powershell
$jobs = 1..10 | ForEach-Object {
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
```

Kết quả mong đợi: `1 × 200 OK`, `9 × 409`.

## 4. Sự kiện + thanh toán (C2, C3, C4) – 2 phút

Trên web: đăng nhập alice → Lịch sử → Thanh toán booking vừa tạo. Sau đó:

```powershell
docker compose logs payment-service booking-service | Select-String "sự kiện"
```

Chỉ ra hai dòng log trên hai service, hai luồng khác nhau (`nio-8084-exec-*` và `ntContainer#0-*`). Mở RabbitMQ UI → Queues → `booking.payment-completed`.

## 5. Chịu lỗi (C8) – 2 phút

```powershell
docker compose stop room-service
# Admin → Quản lý booking: vẫn hiện, tên phòng "N/A"
Invoke-RestMethod http://localhost:8088/actuator/health
docker compose start room-service

docker compose stop rabbitmq
# Thanh toán một booking khác trên web: vẫn thành công (fallback REST)
docker compose logs payment-service | Select-String "chuyển sang gọi REST"
docker compose start rabbitmq
```

## 6. Sao lưu và khôi phục (C7) – 2 phút

```powershell
docker compose exec db-backup sh /scripts/backup-once.sh
ls backups
docker compose exec postgres psql -U hotel -d bookingdb -c "DELETE FROM booking"   # "sự cố"
docker compose stop booking-service
docker compose exec db-backup sh /scripts/restore.sh <tên-thư-mục-vừa-tạo> bookingdb
docker compose start booking-service
# Mở lại trang Lịch sử: booking đã trở lại
```

## Dọn dẹp

```powershell
docker compose down        # giữ dữ liệu
docker compose down -v     # xoá cả dữ liệu
```
