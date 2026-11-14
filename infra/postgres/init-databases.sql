-- Chạy một lần khi volume PostgreSQL còn trống.
-- Database-per-service: mỗi microservice sở hữu database riêng, không service nào đọc thẳng database của service khác.
CREATE DATABASE userdb;
CREATE DATABASE roomdb;
CREATE DATABASE bookingdb;
CREATE DATABASE paymentdb;
