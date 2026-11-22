package com.umutberk.eventreservation.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Giris basarisiz -> 401 INVALID_CREDENTIALS. E-posta mi yoksa sifre mi yanlis, bilerek SOYLENMEZ
 * (hangi e-postalarin kayitli oldugu bu yolla ogrenilemesin).
 */
public class InvalidCredentialsException extends ErrorResponseException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, problem(), null);
    }

    private static ProblemDetail problem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        problem.setTitle("Invalid credentials");
        problem.setProperty("code", "INVALID_CREDENTIALS");
        return problem;
    }
}
