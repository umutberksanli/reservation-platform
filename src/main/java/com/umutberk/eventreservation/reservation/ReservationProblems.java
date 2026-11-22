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

    /**
     * Kullanicinin bu etkinlikte zaten aktif (onayli ya da bekleyen) kaydi var -> 409 ALREADY_RESERVED.
     * Biliniyorsa mevcut durum "currentStatus" alaninda doner.
     */
    public static class AlreadyReservedException extends ErrorResponseException {
        public AlreadyReservedException(long eventId, long userId, ReservationStatus currentStatus, Throwable cause) {
            super(HttpStatus.CONFLICT, problem(eventId, userId, currentStatus), cause);
        }

        private static ProblemDetail problem(long eventId, long userId, ReservationStatus currentStatus) {
            String what = currentStatus == ReservationStatus.WAITLISTED ? "is already on the waitlist"
                    : currentStatus == ReservationStatus.CONFIRMED ? "already has a confirmed reservation"
                    : "already has an active reservation or waitlist entry";
            ProblemDetail problem = ReservationProblems.problem(HttpStatus.CONFLICT, "Already reserved",
                    "ALREADY_RESERVED", "User " + userId + " " + what + " for event " + eventId);
            if (currentStatus != null) {
                problem.setProperty("currentStatus", currentStatus.name());
            }
            return problem;
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

    /** Kullanicinin bu etkinlikte onayli ya da bekleyen kaydi yok -> 404 NO_ACTIVE_RESERVATION */
    public static class NoActiveReservationException extends ErrorResponseException {
        public NoActiveReservationException(long eventId, long userId) {
            super(HttpStatus.NOT_FOUND,
                    problem(HttpStatus.NOT_FOUND, "No active reservation", "NO_ACTIVE_RESERVATION",
                            "User " + userId + " has no confirmed or waitlisted reservation for event " + eventId),
                    null);
        }
    }

    static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
