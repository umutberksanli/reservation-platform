package com.umutberk.eventreservation.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Rezervasyon hatalari. Hepsi RFC 9457 problem+json doner ve istemcinin
 * ayni HTTP koduna sahip hatalari ayirt edebilmesi icin bir "code" alani tasir.
 */
public final class ReservationProblems {

    private ReservationProblems() {
    }

    /** Etkinligin kapasitesi dolu -> 409 EVENT_FULL */
    public static class EventFullException extends ErrorResponseException {
        public EventFullException(long eventId) {
            super(HttpStatus.CONFLICT,
                    problem(HttpStatus.CONFLICT, "Event is full", "EVENT_FULL",
                            "Event " + eventId + " has no free places left"),
                    null);
        }
    }

    /** Kullanicinin bu etkinlikte zaten aktif rezervasyonu var -> 409 ALREADY_RESERVED */
    public static class AlreadyReservedException extends ErrorResponseException {
        public AlreadyReservedException(long eventId, long userId, Throwable cause) {
            super(HttpStatus.CONFLICT,
                    problem(HttpStatus.CONFLICT, "Already reserved", "ALREADY_RESERVED",
                            "User " + userId + " already has an active reservation for event " + eventId),
                    cause);
        }
    }

    /** Istenen ID'de rezervasyon yok -> 404 RESERVATION_NOT_FOUND */
    public static class ReservationNotFoundException extends ErrorResponseException {
        public ReservationNotFoundException(long id) {
            super(HttpStatus.NOT_FOUND,
                    problem(HttpStatus.NOT_FOUND, "Reservation not found", "RESERVATION_NOT_FOUND",
                            "Reservation " + id + " was not found"),
                    null);
        }
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
