#!/bin/sh
# Chương 7 – Sao lưu tự động: chạy backup-once.sh mỗi BACKUP_INTERVAL_SECONDS giây.
INTERVAL="${BACKUP_INTERVAL_SECONDS:-3600}"
echo "[backup] Bắt đầu sao lưu tự động, chu kỳ ${INTERVAL}s, giữ ${BACKUP_KEEP:-24} bản"

while true; do
  sh /scripts/backup-once.sh
  sleep "$INTERVAL"
done
