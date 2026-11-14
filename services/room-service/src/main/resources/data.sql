-- Dữ liệu mẫu 20 phòng. Mỗi câu chỉ chèn khi phòng đó chưa có, nên chạy lại nhiều lần
-- (hoặc 2 instance room-service khởi động cùng lúc) vẫn không tạo bản ghi trùng.
-- Id cố định để khớp với ảnh phòng ở frontend (images-room-<id>-*.jpg).
INSERT INTO rooms (id, name, capacity, price) SELECT 1, 'Standard 101', 2, 800000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 1);
INSERT INTO rooms (id, name, capacity, price) SELECT 2, 'Standard 102', 2, 800000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 2);
INSERT INTO rooms (id, name, capacity, price) SELECT 3, 'Standard 103', 2, 850000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 3);
INSERT INTO rooms (id, name, capacity, price) SELECT 4, 'Standard 104', 2, 850000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 4);
INSERT INTO rooms (id, name, capacity, price) SELECT 5, 'Deluxe 201', 3, 1200000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 5);
INSERT INTO rooms (id, name, capacity, price) SELECT 6, 'Deluxe 202', 3, 1200000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 6);
INSERT INTO rooms (id, name, capacity, price) SELECT 7, 'Deluxe 203', 3, 1250000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 7);
INSERT INTO rooms (id, name, capacity, price) SELECT 8, 'Deluxe 204', 3, 1250000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 8);
INSERT INTO rooms (id, name, capacity, price) SELECT 9, 'Family 301', 4, 1600000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 9);
INSERT INTO rooms (id, name, capacity, price) SELECT 10, 'Family 302', 4, 1600000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 10);
INSERT INTO rooms (id, name, capacity, price) SELECT 11, 'Family 303', 4, 1650000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 11);
INSERT INTO rooms (id, name, capacity, price) SELECT 12, 'Family 304', 4, 1650000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 12);
INSERT INTO rooms (id, name, capacity, price) SELECT 13, 'Suite 401', 2, 2200000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 13);
INSERT INTO rooms (id, name, capacity, price) SELECT 14, 'Suite 402', 2, 2200000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 14);
INSERT INTO rooms (id, name, capacity, price) SELECT 15, 'Suite 403', 2, 2300000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 15);
INSERT INTO rooms (id, name, capacity, price) SELECT 16, 'Presidential 501', 4, 3500000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 16);
INSERT INTO rooms (id, name, capacity, price) SELECT 17, 'Presidential 502', 4, 3500000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 17);
INSERT INTO rooms (id, name, capacity, price) SELECT 18, 'Penthouse 601', 3, 3000000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 18);
INSERT INTO rooms (id, name, capacity, price) SELECT 19, 'Penthouse 602', 3, 3000000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 19);
INSERT INTO rooms (id, name, capacity, price) SELECT 20, 'Royal Suite 701', 2, 4000000 WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE id = 20);
