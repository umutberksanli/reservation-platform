package com.umutberk.eventreservation.reservation;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.umutberk.eventreservation.reservation.dto.CreateReservationRequest;
import com.umutberk.eventreservation.reservation.dto.ReservationResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 201 Created + Location: /api/reservations/{id}
     * 404 EVENT_NOT_FOUND / USER_NOT_FOUND, 409 ALREADY_RESERVED / EVENT_FULL, 400 gecersiz govde.
     */
    @PostMapping("/events/{eventId}/reservations")
    public ResponseEntity<ReservationResponse> create(@PathVariable long eventId,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.create(eventId, request.userId());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/reservations/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@PathVariable long id) {
        return reservationService.findById(id);
    }
}
