package com.umutberk.eventreservation.reservation.dto;

import java.util.List;

/**
 * POST /api/reservations/{id}/cancel cevabi.
 *
 * @param reservation      iptal edilen kaydin guncel hali (status = CANCELLED)
 * @param alreadyCancelled true ise kayit zaten iptal edilmisti; bu istek hicbir sey degistirmedi
 * @param promoted         ayni transaction'da bekleme listesinden onayliya gecen kayitlar (genelde 0 ya da 1)
 */
public record CancellationResponse(
        ReservationResponse reservation,
        boolean alreadyCancelled,
        List<ReservationResponse> promoted) {
}
