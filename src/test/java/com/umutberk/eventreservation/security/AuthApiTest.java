package com.umutberk.eventreservation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Gercek token'larla uctan uca: kayit -> giris -> token ile korumali uc.
 * Buradaki istekler TestAuth kisayolunu KULLANMAZ; token'lar gercek JwtDecoder'dan gecer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class AuthApiTest {

    private static final String PASSWORD = "cok-gizli-sifre-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder appEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void registerLoginAndUseTheToken() throws Exception {
        long id = register("Login.Test@Example.com");

        String body = login("login.test@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginIsCaseInsensitiveForEmail() throws Exception {
        register("case@example.com");

        login("  CASE@Example.COM ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        register("known@example.com");

        String wrongPassword = login("known@example.com", "yanlis-sifre-99")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", "yanlis-sifre-99")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        // Hangi e-postanin kayitli oldugu cevaptan anlasilamamali.
        assertThat(JsonPath.<String>read(wrongPassword, "$.detail")).isEqualTo(JsonPath.read(unknownEmail, "$.detail"));
    }

    @Test
    void userCreatedBeforeV4WithoutPasswordCannotLogIn() throws Exception {
        jdbc.update("INSERT INTO app_user (email, full_name) VALUES ('legacy@example.com', 'Eski Kullanici')");

        login("legacy@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        mockMvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"a@b.c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        long id = register("expired@example.com");
        String token = sign(appEncoder, jwtProperties.issuer(), id, Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600));

        expectUnauthorized(token);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        long id = register("forged@example.com");
        JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "baska-bir-anahtar-baska-bir-anahtar!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String token = sign(attacker, jwtProperties.issuer(), id, Instant.now(), Instant.now().plusSeconds(600));

        expectUnauthorized(token);
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        long id = register("tamper@example.com");
        String token = sign(appEncoder, jwtProperties.issuer(), id, Instant.now(), Instant.now().plusSeconds(600));
        String[] parts = token.split("\\.");
        // Imza ayni kalir, govde (sub) degistirilir -> imza tutmaz.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"" + jwtProperties.issuer() + "\",\"sub\":\"1\",\"roles\":[\"ADMIN\"],\"exp\":"
                        + (Instant.now().getEpochSecond() + 600) + "}").getBytes(StandardCharsets.UTF_8));

        expectUnauthorized(parts[0] + "." + forgedPayload + "." + parts[2]);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        long id = register("issuer@example.com");
        String token = sign(appEncoder, "baska-bir-sistem", id, Instant.now(), Instant.now().plusSeconds(600));

        expectUnauthorized(token);
    }

    // ------------------------------------------------------------ yardimcilar

    private void expectUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static String sign(JwtEncoder encoder, String issuer, long userId, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("USER"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private long register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "fullName": "Auth Testi", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }
}
