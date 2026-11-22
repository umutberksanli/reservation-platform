package com.umutberk.eventreservation.reservation;

import java.util.List;

/** V3'teki chk_reservation_status ile ayni degerler. */
public enum ReservationStatus {
    /** Yer ayrildi; kapasiteden duser. */
    CONFIRMED,
    /** Etkinlik dolu; bekleme listesinde, waitlist_number sirasiyla bekler. */
    WAITLISTED,
    /** Iptal edildi; kapasiteden dusmez, siradan cikmistir. */
    CANCELLED;

    /** "Aktif" kayitlar: V3'teki active_key ile ayni tanim. */
    public static final List<ReservationStatus> ACTIVE = List.of(CONFIRMED, WAITLISTED);
}
