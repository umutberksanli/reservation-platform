-- V3: bekleme listesi. Bekleyen kayit da bir "reservation" satiridir (status = 'WAITLISTED').
-- Boylece "ayni kullanici ayni etkinlikte hem onayli hem bekleyen olamaz" ve
-- "siraya iki kez giremez" kurallari TEK bir UNIQUE indeksle veritabaninda korunur.
-- V1 ve V2 degistirilmez.

-- 1) Eski durum kurali ve "aktif" tanimina bagli UNIQUE indeks kaldirilir (asagida yeniden kurulur).
ALTER TABLE reservation DROP CHECK chk_reservation_status;
ALTER TABLE reservation DROP INDEX uk_reservation_active_user_event;

-- 2) "Aktif" artik onayli VEYA bekleyen demek.
ALTER TABLE reservation
    MODIFY COLUMN active_key TINYINT
        GENERATED ALWAYS AS (CASE WHEN status IN ('CONFIRMED', 'WAITLISTED') THEN 1 ELSE NULL END) VIRTUAL;

-- 3) Yeni sutunlar.
--    waitlist_number: etkinlik icindeki kalici sira numarasi (1, 2, 3 ...). Bekleme listesine girerken
--    verilir, terfi/iptalden sonra da satirda kalir; bu yuzden bir numara asla tekrar kullanilmaz.
ALTER TABLE reservation
    ADD COLUMN waitlist_number INT         NULL,
    ADD COLUMN promoted_at     DATETIME(6) NULL,
    ADD COLUMN cancelled_at    DATETIME(6) NULL;

-- 4) Kurallar.
ALTER TABLE reservation
    ADD CONSTRAINT chk_reservation_status
        CHECK (status IN ('CONFIRMED', 'WAITLISTED', 'CANCELLED')),
    -- Ayni kullanici ayni etkinlikte en fazla bir aktif (onayli ya da bekleyen) kayit.
    ADD CONSTRAINT uk_reservation_active_user_event UNIQUE (event_id, user_id, active_key),
    -- Sira numarasi etkinlik icinde tek anlamli.
    -- DIKKAT: sutun sirasi bilerek (waitlist_number, event_id). MySQL 9.7'de bu indeks
    -- (event_id, waitlist_number) olarak sonradan eklendiginde InnoDB onu foreign key indeksi
    -- olarak seciyor ve fk_reservation_event kontrolunu sessizce birakiyordu (olmayan etkinlige
    -- kayit eklenebildi; SchemaConstraintsTest yakaladi). Iki sutunlu UNIQUE'te sira anlami degistirmez.
    ADD CONSTRAINT uk_reservation_event_waitlist_number UNIQUE (waitlist_number, event_id),
    ADD CONSTRAINT chk_reservation_waitlist_number_positive
        CHECK (waitlist_number IS NULL OR waitlist_number > 0),
    -- Bekleyen her kaydin bir sira numarasi olmali.
    ADD CONSTRAINT chk_reservation_waitlisted_has_number
        CHECK (status <> 'WAITLISTED' OR waitlist_number IS NOT NULL);

-- 5) "Siradaki bekleyen" ve sayim sorgulari icin indeks; V2'deki (event_id, status) indeksini kapsar.
ALTER TABLE reservation ADD INDEX idx_reservation_event_status_waitlist (event_id, status, waitlist_number);
ALTER TABLE reservation DROP INDEX idx_reservation_event_status;
