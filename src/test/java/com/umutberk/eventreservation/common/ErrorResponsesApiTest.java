package com.umutberk.eventreservation.common;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.umutberk.eventreservation.support.TestAuth.asAdmin;
import static com.umutberk.eventreservation.support.TestAuth.asUser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hata cevaplarinin tek bicimde oldugunu dogrular: application/problem+json, her cevapta
 * "code" ve "instance", dogrulama hatalarinda alan listesi, 500'de ic ayrinti sizmamasi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ErrorResponsesApiTest.FailingController.class)
class ErrorResponsesApiTest {

    /** Yalnizca bu testte yuklenir: beklenmeyen hata ve kilit zaman asimi uretir. */
    @RestController
    static class FailingController {
        @GetMapping("/test-only/boom")
        String boom() {
            throw new IllegalStateException("gizli-ic-ayrinti: db sifresi vb.");
        }

        @GetMapping("/test-only/lock-timeout")
        String lockTimeout() {
            throw new CannotAcquireLockException("Lock wait timeout exceeded");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidBodyListsEveryInvalidField() throws Exception {
        problem(mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"fullName\": \"\", \"password\": \"gecerli-sifre-1\"}")),
                400, "VALIDATION_FAILED", "/api/users")
                .andExpect(jsonPath("$.detail").value("Request body has invalid fields"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "fullName")))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void invalidQueryParameterIsReportedByName() throws Exception {
        problem(mockMvc.perform(get("/api/users/1/reservations?size=0").with(asAdmin(1))), 400, "VALIDATION_FAILED",
                "/api/users/1/reservations")
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void malformedJson() throws Exception {
        problem(mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{not json")),
                400, "MALFORMED_REQUEST", "/api/users");
    }

    @Test
    void wrongPathVariableType() throws Exception {
        problem(mockMvc.perform(get("/api/events/abc")), 400, "INVALID_PARAMETER", "/api/events/abc");
    }

    @Test
    void unknownPath() throws Exception {
        problem(mockMvc.perform(get("/api/does-not-exist").with(asUser(1))), 404, "NOT_FOUND", "/api/does-not-exist");
    }

    @Test
    void wrongMethodListsAllowedMethods() throws Exception {
        problem(mockMvc.perform(delete("/api/events").with(asUser(1))), 405, "METHOD_NOT_ALLOWED", "/api/events")
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void unsupportedMediaType() throws Exception {
        problem(mockMvc.perform(post("/api/users").contentType(MediaType.TEXT_PLAIN).content("x")),
                415, "UNSUPPORTED_MEDIA_TYPE", "/api/users");
    }

    @Test
    void domainErrorsKeepTheirCodeAndGetInstance() throws Exception {
        problem(mockMvc.perform(get("/api/events/999999999")), 404, "EVENT_NOT_FOUND", "/api/events/999999999")
                .andExpect(jsonPath("$.title").value("Event not found"));
    }

    @Test
    void unexpectedErrorDoesNotLeakDetails() throws Exception {
        problem(mockMvc.perform(get("/test-only/boom").with(asUser(1))), 500, "INTERNAL_ERROR", "/test-only/boom")
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("gizli-ic-ayrinti"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void lockTimeoutAsksClientToRetry() throws Exception {
        problem(mockMvc.perform(get("/test-only/lock-timeout").with(asUser(1))), 503, "SERVICE_BUSY", "/test-only/lock-timeout")
                .andExpect(header().string("Retry-After", "1"));
    }

    @Test
    void missingTokenOnProtectedEndpoint() throws Exception {
        problem(mockMvc.perform(get("/api/users/me")), 401, "UNAUTHORIZED", "/api/users/me")
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void invalidTokenIsRejectedByTheRealDecoder() throws Exception {
        problem(mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer bozuk.token.degeri")),
                401, "UNAUTHORIZED", "/api/users/me")
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    @Test
    void insufficientRole() throws Exception {
        problem(mockMvc.perform(post("/api/events").with(asUser(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"X\", \"startsAt\": \"2027-01-15T19:00:00Z\", \"capacity\": 3}")),
                403, "FORBIDDEN", "/api/events");
    }

    /** Ortak bicim: dogru durum kodu, problem+json, code ve instance. */
    private static ResultActions problem(ResultActions result, int status, String code, String instance)
            throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.instance").value(instance));
    }
}
