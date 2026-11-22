-- V1: ilk sema. Etkinlikler ve rezervasyon yapacak kullanicilar.
-- Bu dosya bir kez uygulandiktan sonra DEGISTIRILMEZ; degisiklikler V2, V3... ile yapilir.

CREATE TABLE app_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(254) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT chk_app_user_email_not_blank CHECK (CHAR_LENGTH(TRIM(email)) > 0),
    CONSTRAINT chk_app_user_full_name_not_blank CHECK (CHAR_LENGTH(TRIM(full_name)) > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE event (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    starts_at   DATETIME(6)  NOT NULL,
    capacity    INT          NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_event_capacity_positive CHECK (capacity > 0),
    CONSTRAINT chk_event_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    INDEX idx_event_starts_at (starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
