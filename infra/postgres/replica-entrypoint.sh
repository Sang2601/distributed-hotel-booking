#!/bin/sh
# Chương 7 – PostgreSQL replica (streaming replication, hot standby).
# Lần đầu: sao chép toàn bộ dữ liệu từ primary bằng pg_basebackup (-R tạo sẵn standby.signal và
# thông tin kết nối). Sau đó replica nhận WAL liên tục từ primary và cho phép truy vấn chỉ-đọc.
set -e

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "[replica] Sao chép dữ liệu ban đầu từ primary..."
  mkdir -p "$PGDATA"
  chown postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"
  until su-exec postgres pg_basebackup -D "$PGDATA" -Fp -Xs -R \
        -d "host=postgres port=5432 user=replicator password=${REPLICATION_PASSWORD}"; do
    echo "[replica] Primary chưa sẵn sàng, thử lại sau 3 giây"
    rm -rf "${PGDATA:?}"/*
    sleep 3
  done
  echo "[replica] Sao chép xong, chuyển sang chế độ standby"
fi

chown -R postgres:postgres "$PGDATA"
chmod 700 "$PGDATA"
exec su-exec postgres postgres -c hot_standby=on -c hot_standby_feedback=on
