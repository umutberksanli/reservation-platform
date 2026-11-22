package com.umutberk.eventreservation.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Ayni e-postayla ikinci kayit denemesi; Spring bunu 409 Conflict cevabina cevirir. */
public class EmailAlreadyRegisteredException extends ErrorResponseException {

    public EmailAlreadyRegisteredException(Throwable cause) {
        super(HttpStatus.CONFLICT, problem(), cause);
    }

    private static ProblemDetail problem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A user with this email address is already registered");
        problem.setTitle("Email already registered");
        return problem;
    }
}
