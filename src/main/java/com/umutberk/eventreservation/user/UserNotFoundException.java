package com.umutberk.eventreservation.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Istenen ID'de kullanici yoksa firlatilir; Spring bunu 404 cevabina cevirir. */
public class UserNotFoundException extends ErrorResponseException {

    public UserNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, problem(id), null);
    }

    private static ProblemDetail problem(long id) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "User " + id + " was not found");
        problem.setTitle("User not found");
        problem.setProperty("code", "USER_NOT_FOUND");
        return problem;
    }
}
