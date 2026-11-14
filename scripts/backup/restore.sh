#!/bin/sh
# Chương 7 – Khôi phục từ một bản sao lưu.
# Cách dùng (trong container db-backup):
#   sh /scripts/restore.sh <thư-mục-backup> [database ...]
# Ví dụ:
#   sh /scripts/restore.sh 20261114-153000              # khôi phục mọi database có trong bản đó
#   sh /scripts/restore.sh 20261114-153000 bookingdb    # chỉ khôi phục bookingdb

set -e

if [ -z "$1" ]; then
  echo "Thiếu tên thư mục backup. Các bản hiện có:"
  ls -1 /backups
  exit 1
fi

DIR="/backups/$1"
shift

if [ ! -d "$DIR" ]; then
  echo "Không tìm thấy $DIR"
  exit 1
fi

if [ $# -gt 0 ]; then
  DBS="$*"
else
  DBS=$(ls "$DIR" | sed -n 's/\.sql\.gz$//p')
fi

for DB in $DBS; do
  FILE="$DIR/$DB.sql.gz"
  if [ ! -f "$FILE" ]; then
    echo "[restore] Bỏ qua $DB: không có $FILE"
    continue
  fi
  echo "[restore] $DB <- $FILE"
  gunzip -c "$FILE" | psql -q -v ON_ERROR_STOP=1 -d "$DB" > /dev/null
done

echo "[restore] Xong."
