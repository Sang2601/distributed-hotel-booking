# Chương 7 – Sao lưu

## 7.1. Dữ liệu bền vững

Phiên bản ban đầu dùng H2 in-memory: tắt service là mất toàn bộ dữ liệu. Hiện tại khi chạy bằng Docker:

- Dữ liệu nằm trong **PostgreSQL 16**, mỗi service một database (`userdb`, `roomdb`, `bookingdb`, `paymentdb`).
- Thư mục dữ liệu của PostgreSQL gắn vào **Docker volume** `pgdata`: xoá và tạo lại container không mất dữ liệu.

## 7.2. Sao lưu tự động

Container `db-backup` chạy [backup-loop.sh](../scripts/backup/backup-loop.sh), cứ mỗi `BACKUP_INTERVAL_SECONDS` giây (mặc định 3600) gọi [backup-once.sh](../scripts/backup/backup-once.sh):

```sh
pg_dump --clean --if-exists --no-owner -d "$DB" > "$DIR/$DB.sql" && gzip "$DIR/$DB.sql"
```

| Thiết lập | Giá trị | Ý nghĩa |
|---|---|---|
| Công cụ | `pg_dump` | Bản sao lưu logic, nhất quán tại thời điểm chụp, không cần dừng database |
| Nơi lưu | `./backups/<yyyyMMdd-HHmmss>/<db>.sql.gz` trên máy host | Nằm ngoài container, vẫn còn khi xoá container |
| Chu kỳ | `BACKUP_INTERVAL_SECONDS=3600` | Mất tối đa 1 giờ dữ liệu khi sự cố (RPO ≈ 1 giờ) |
| Giữ lại | `BACKUP_KEEP=24` | Tự xoá bản cũ, giữ 24 bản gần nhất |
| `--clean --if-exists` | | File backup tự xoá bảng cũ trước khi tạo lại, khôi phục đè lên được |

Đổi chu kỳ: tạo file `.env` cạnh `docker-compose.yml`, ví dụ `BACKUP_INTERVAL_SECONDS=600`, rồi chạy lại `docker compose up -d`.

## 7.3. Sao lưu thủ công

```powershell
docker compose exec db-backup sh /scripts/backup-once.sh
```

## 7.4. Khôi phục

```powershell
# 1. Xem các bản backup
ls backups

# 2. Dừng service đang dùng database cần khôi phục
docker compose stop booking-service

# 3. Khôi phục (bỏ tên database để khôi phục tất cả)
docker compose exec db-backup sh /scripts/restore.sh 20261114-014405 bookingdb

# 4. Chạy lại service
docker compose start booking-service
```

**Kết quả kiểm thử**: bảng `booking` có 3 dòng → sao lưu → xoá toàn bộ bảng (0 dòng) → khôi phục → 3 dòng, API trả lại đúng các booking.

## 7.5. Hướng mở rộng

- Đẩy thư mục `backups` lên lưu trữ đám mây (S3, Google Cloud Storage) để chống mất máy.
- Thêm PostgreSQL replica (streaming replication) để có bản sao thời gian thực, giảm RPO về gần 0.
