-- V4: kimlik dogrulama ve yetki. V1-V3 degistirilmez.
--
-- password_hash: BCrypt ozeti ("{bcrypt}$2a$10$..."), duz sifre ASLA saklanmaz.
--   NULL olabilir: bu migration'dan once olusmus kullanicilarin sifresi yoktur; giris yapamazlar
--   (yeni kayitlarda her zaman doludur).
-- role: USER (varsayilan) ya da ADMIN. Etkinlik olusturma ve baskalarinin kayitlarini yonetme ADMIN'e aittir.

ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(100) NULL,
    ADD COLUMN role          VARCHAR(20)  NOT NULL DEFAULT 'USER';

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_role CHECK (role IN ('USER', 'ADMIN'));
