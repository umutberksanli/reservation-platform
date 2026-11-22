package com.umutberk.eventreservation.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Kimligi belli ama bu islem icin yetkisi yok -> 403 FORBIDDEN. */
public class ForbiddenException extends ErrorResponseException {

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, problem(detail), null);
    }

    private static ProblemDetail problem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        problem.setTitle("Forbidden");
        problem.setProperty("code", "FORBIDDEN");
        return problem;
    }
}
