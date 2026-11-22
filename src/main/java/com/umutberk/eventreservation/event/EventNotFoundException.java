package com.umutberk.eventreservation.event;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Istenen ID'de etkinlik yoksa firlatilir; Spring bunu otomatik olarak 404 cevabina cevirir. */
public class EventNotFoundException extends ErrorResponseException {

    public EventNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, problem(id), null);
    }

    private static ProblemDetail problem(long id) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Event " + id + " was not found");
        problem.setTitle("Event not found");
        problem.setProperty("code", "EVENT_NOT_FOUND");
        return problem;
    }
}
