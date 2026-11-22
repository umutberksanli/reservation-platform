package com.umutberk.eventreservation.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tum hata cevaplarini tek bicime sokar (RFC 9457 application/problem+json):
 *
 *   {"type", "title", "status", "detail", "instance", "code", ["errors"]}
 *
 * - "code" her cevapta vardir; istemci HTTP kodu ayni olan hatalari bununla ayirt eder.
 * - "instance" istegin yoludur.
 * - Dogrulama hatalarinda "errors" alan bazinda liste doner.
 * - Beklenmeyen hatalarda ic ayrinti istemciye sizdirilmaz; yalnizca sunucu loguna yazilir.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // -------------------------------------------------------------- Spring'in standart hatalari

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Request body has invalid fields");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> fieldError(e.getField(), e.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(fieldError(name, error.getDefaultMessage()));
            }
        });
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Request parameters are invalid");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Butun standart ve uygulama (ErrorResponseException) hatalari buradan gecer:
     * eksikse "code" ve "instance" eklenir.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        // Spring, ErrorResponseException'lari (bizim EventNotFoundException vb.) body = null ile gonderir;
        // o durumda hatanin kendi ProblemDetail'i kullanilmali, yoksa "code" gibi alanlar kaybolur.
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd
                : ex instanceof org.springframework.web.ErrorResponse errorResponse ? errorResponse.getBody()
                : ProblemDetail.forStatusAndDetail(statusCode, "Request could not be processed");
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey("code")) {
            problem.setProperty("code", codeFor(ex, statusCode));
        }
        if (problem.getInstance() == null) {
            problem.setInstance(path(request));
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    // -------------------------------------------------------------- uygulamaya ozgu yakalamalar

    /** Kilit beklerken zaman asimi / deadlock: istemci kisa sure sonra tekrar denesin. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handleLockFailure(PessimisticLockingFailureException ex, WebRequest request) {
        log.warn("Lock could not be acquired for {}: {}", path(request), ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The resource is busy, please retry shortly");
        problem.setTitle("Service busy");
        problem.setProperty("code", "SERVICE_BUSY");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "1");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    /** Servislerin ozel olarak cevirmedigi bir veritabani kurali ihlali. Ic mesaj sizdirilmaz. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Unmapped data integrity violation for {}", path(request), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with existing data");
        problem.setTitle("Data conflict");
        problem.setProperty("code", "DATA_CONFLICT");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /**
     * Controller icinden yukari cikan Spring Security hatalari (filtre seviyesindekiler
     * ProblemJsonSecurityHandlers'ta karsilanir). Aksi halde asagidaki genel 500'e duserlerdi.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission for this operation");
        problem.setTitle("Forbidden");
        problem.setProperty("code", "FORBIDDEN");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required");
        problem.setTitle("Unauthorized");
        problem.setProperty("code", "UNAUTHORIZED");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.UNAUTHORIZED, request);
    }

    /** Beklenmeyen her sey: 500, genel mesaj; ayrinti yalnizca sunucu loguna. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error for {}", path(request), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setProperty("code", "INTERNAL_ERROR");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    // -------------------------------------------------------------- yardimcilar

    private static String codeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return "VALIDATION_FAILED";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "MALFORMED_REQUEST";
        }
        if (ex instanceof TypeMismatchException || ex instanceof MissingServletRequestParameterException) {
            return "INVALID_PARAMETER";
        }
        if (ex instanceof NoResourceFoundException) {
            return "NOT_FOUND";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return "METHOD_NOT_ALLOWED";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return "UNSUPPORTED_MEDIA_TYPE";
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return "NOT_ACCEPTABLE";
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field == null ? "" : field, "message", message == null ? "invalid" : message);
    }

    private static URI path(WebRequest request) {
        if (request instanceof NativeWebRequest nativeRequest) {
            HttpServletRequest servlet = nativeRequest.getNativeRequest(HttpServletRequest.class);
            if (servlet != null) {
                return URI.create(servlet.getRequestURI());
            }
        }
        return null;
    }
}
