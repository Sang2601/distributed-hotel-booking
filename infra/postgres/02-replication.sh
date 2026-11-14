#!/bin/sh
# Chương 7 – Chuẩn bị cho replica: tạo user replicator và cho phép kết nối replication.
# Chạy một lần khi volume PostgreSQL primary còn trống.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c \
  "CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';"

echo "host replication replicator all scram-sha-256" >> "$PGDATA/pg_hba.conf"
