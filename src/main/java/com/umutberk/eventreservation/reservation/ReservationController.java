package com.umutberk.eventreservation.reservation;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.umutberk.eventreservation.common.PageResponse;
import com.umutberk.eventreservation.reservation.dto.CancellationResponse;
import com.umutberk.eventreservation.reservation.dto.CreateReservationRequest;
import com.umutberk.eventreservation.reservation.dto.ReservationResponse;
import com.umutberk.eventreservation.reservation.dto.UserReservationResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 201 Created  -> yer ayrildi (CONFIRMED)
     * 202 Accepted -> etkinlik dolu, bekleme listesine alindi (WAITLISTED, waitlistPosition dolu)
     * Ikisinde de Location: /api/reservations/{id}
     * 404 EVENT_NOT_FOUND / USER_NOT_FOUND, 409 ALREADY_RESERVED, 400 gecersiz govde.
     */
    @PostMapping("/events/{eventId}/reservations")
    public ResponseEntity<ReservationResponse> create(@PathVariable long eventId,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.create(eventId, request.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/reservations/{id}")
                .buildAndExpand(created.id())
                .toUri();
        HttpStatus status = created.status() == ReservationStatus.CONFIRMED ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).location(location).body(created);
    }

    /** Durum ve (bekliyorsa) sira konumu. 404 RESERVATION_NOT_FOUND */
    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@PathVariable long id) {
        return reservationService.findById(id);
    }

    /** Kullanicinin bu etkinlikteki aktif kaydi. 404 EVENT_NOT_FOUND / USER_NOT_FOUND / NO_ACTIVE_RESERVATION */
    @GetMapping("/events/{eventId}/users/{userId}/reservation")
    public ReservationResponse getActiveForUser(@PathVariable long eventId, @PathVariable long userId) {
        return reservationService.findActive(eventId, userId);
    }

    /**
     * Kullanicinin kendi kayitlari (onayli, bekleyen, iptal), en yeni once, sayfali.
     * status: CONFIRMED | WAITLISTED | CANCELLED (bos = hepsi); page >= 0; size 1..100.
     * 404 USER_NOT_FOUND, 400 gecersiz parametre.
     */
    @GetMapping("/users/{userId}/reservations")
    public PageResponse<UserReservationResponse> listForUser(@PathVariable long userId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reservationService.listForUser(userId, status, page, size);
    }

    /**
     * Iptal. 200 OK; onayli bir yer bosaldiysa "promoted" listesinde terfi eden kayit doner.
     * Tekrar iptal: yine 200, alreadyCancelled = true, hicbir sey degismez. 404 RESERVATION_NOT_FOUND
     */
    @PostMapping("/reservations/{id}/cancel")
    public CancellationResponse cancel(@PathVariable long id) {
        return reservationService.cancel(id);
    }
}
