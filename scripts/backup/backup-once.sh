#!/bin/sh
# Chương 7 – Sao lưu một lần tất cả database bằng pg_dump.
# Chạy bên trong container db-backup (đã có PGHOST/PGUSER/PGPASSWORD).
# PGHOST trỏ tới REPLICA: sao lưu không làm nặng node primary đang phục vụ ghi.
# Kết quả: /backups/<thời-gian>/<database>.sql.gz

TS=$(date +%Y%m%d-%H%M%S)
DIR="/backups/$TS"
KEEP="${BACKUP_KEEP:-24}"
FAILED=0

mkdir -p "$DIR"

for DB in $BACKUP_DATABASES; do
  # --clean --if-exists: file backup tự xoá bảng cũ trước khi tạo lại, nên khôi phục đè lên được
  if pg_dump --clean --if-exists --no-owner -d "$DB" > "$DIR/$DB.sql"; then
    gzip "$DIR/$DB.sql"
    echo "[backup] $(date '+%F %T') $DB -> $DIR/$DB.sql.gz"
  else
    echo "[backup] $(date '+%F %T') LỖI khi sao lưu $DB" >&2
    rm -f "$DIR/$DB.sql"
    FAILED=1
  fi
done

# Chỉ giữ KEEP bản gần nhất
COUNT=$(ls -1d /backups/2*/ 2>/dev/null | wc -l)
EXCESS=$((COUNT - KEEP))
if [ "$EXCESS" -gt 0 ]; then
  ls -1d /backups/2*/ | sort | head -n "$EXCESS" | while read -r OLD; do
    rm -rf "$OLD"
    echo "[backup] Xoá bản cũ $OLD"
  done
fi

exit $FAILED
