-- V2: rezervasyonlar. Bir kullanici bir etkinlikte yer ayirir.
-- V1 degistirilmez; bu dosya da uygulandiktan sonra degistirilmez.

CREATE TABLE reservation (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    event_id    BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- Aktif (CONFIRMED) rezervasyonda 1, digerlerinde NULL.
    -- MySQL'de kosullu (partial) unique index olmadigi icin bu hesaplanmis sutun kullanilir:
    -- UNIQUE indekste NULL degerler birbiriyle cakismaz, yani kural yalnizca aktif kayitlara uygulanir.
    active_key  TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'CONFIRMED' THEN 1 ELSE NULL END) VIRTUAL,

    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_event FOREIGN KEY (event_id) REFERENCES event (id),
    CONSTRAINT fk_reservation_user  FOREIGN KEY (user_id)  REFERENCES app_user (id),
    CONSTRAINT chk_reservation_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    -- Ayni kullanici ayni etkinlikte en fazla bir aktif rezervasyon.
    CONSTRAINT uk_reservation_active_user_event UNIQUE (event_id, user_id, active_key),
    -- Etkinlikteki aktif rezervasyonlari saymak icin.
    INDEX idx_reservation_event_status (event_id, status),
    INDEX idx_reservation_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
