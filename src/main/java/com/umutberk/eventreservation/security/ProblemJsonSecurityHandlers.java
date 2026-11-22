package com.umutberk.eventreservation.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guvenlik filtresinde (controller'a ulasmadan) olusan 401/403 cevaplarini da diger hatalarla
 * ayni problem+json bicimine sokar.
 */
final class ProblemJsonSecurityHandlers {

    private ProblemJsonSecurityHandlers() {
    }

    /** Token yok, suresi dolmus ya da imzasi gecersiz -> 401 UNAUTHORIZED. */
    static AuthenticationEntryPoint unauthorized() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) -> {
            boolean tokenSent = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    tokenSent ? "Bearer error=\"invalid_token\"" : "Bearer");
            write(response, 401, "Unauthorized",
                    tokenSent ? "The access token is missing, expired or invalid" : "Authentication is required",
                    request.getRequestURI(), "UNAUTHORIZED");
        };
    }

    /** Giris yapilmis ama rol yetmiyor -> 403 FORBIDDEN. */
    static AccessDeniedHandler forbidden() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) ->
                write(response, 403, "Forbidden", "You do not have permission for this operation",
                        request.getRequestURI(), "FORBIDDEN");
    }

    private static void write(HttpServletResponse response, int status, String title, String detail, String instance,
            String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + json(title) + "\",\"status\":" + status
                + ",\"detail\":\"" + json(detail) + "\",\"instance\":\"" + json(instance) + "\",\"code\":\"" + json(code)
                + "\"}");
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
