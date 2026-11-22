package com.umutberk.eventreservation.reservation;

/** V2'deki chk_reservation_status ile ayni degerler. */
public enum ReservationStatus {
    /** Yer ayrildi; kapasiteden duser. */
    CONFIRMED,
    /** Iptal edildi (iptal ucu henuz yok); kapasiteden dusmez. */
    CANCELLED
}
