package com.umutberk.eventreservation.user;

import static com.umutberk.eventreservation.support.TestAuth.asAdmin;
import static com.umutberk.eventreservation.support.TestAuth.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/users (herkese acik) ve GET /api/users/{id}, /me (giris gerekir) davranisi,
 * gercek MySQL test veritabaninda. Her test sonunda islem geri alinir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class UserRegistrationApiTest {

    private static final String PASSWORD = "dogru-at-pil-zimba";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Gercek repository; yalnizca bir testte on kontrolu devre disi birakmak icin sarmalanir. */
    @MockitoSpyBean
    private AppUserRepository appUserRepository;

    @Test
    void validRegistrationReturns201WithLocationAndNeverExposesPassword() throws Exception {
        register("  Ayse.Yilmaz@Example.com ", "  Ayse Yilmaz ", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/users/" + lastInsertedId())))
                .andExpect(jsonPath("$.id").value(lastInsertedId()))
                .andExpect(jsonPath("$.email").value("ayse.yilmaz@example.com"))
                .andExpect(jsonPath("$.fullName").value("Ayse Yilmaz"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))));

        String stored = jdbc.queryForObject(
                "SELECT CONCAT(email, '|', full_name, '|', role) FROM app_user WHERE id = ?", String.class, lastInsertedId());
        assertThat(stored).isEqualTo("ayse.yilmaz@example.com|Ayse Yilmaz|USER");
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() throws Exception {
        register("hash@example.com", "Hash Testi", PASSWORD).andExpect(status().isCreated());

        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, lastInsertedId());
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        assertThat(passwordEncoder.matches("yanlis-sifre-123", hash)).isFalse();
    }

    @Test
    void registrationCannotChooseAdminRole() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "sinsi@example.com", "fullName": "Sinsi", "password": "%s", "role": "ADMIN"}
                        """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\": \"\", \"fullName\": \"Ayse Yilmaz\", \"password\": \"12345678\"}",              // bos e-posta
            "{\"email\": \"   \", \"fullName\": \"Ayse Yilmaz\", \"password\": \"12345678\"}",           // sadece bosluk
            "{\"email\": \"not-an-email\", \"fullName\": \"Ayse Yilmaz\", \"password\": \"12345678\"}",  // gecersiz bicim
            "{\"fullName\": \"Ayse Yilmaz\", \"password\": \"12345678\"}",                               // e-posta yok
            "{\"email\": \"ayse@example.com\", \"fullName\": \"\", \"password\": \"12345678\"}",         // bos ad
            "{\"email\": \"ayse@example.com\", \"fullName\": \"   \", \"password\": \"12345678\"}",      // sadece bosluk
            "{\"email\": \"ayse@example.com\", \"password\": \"12345678\"}",                             // ad yok
            "{\"email\": \"ayse@example.com\", \"fullName\": \"%1$s\", \"password\": \"12345678\"}",     // 101 karakterlik ad
            "{\"email\": \"ayse@example.com\", \"fullName\": \"Ayse\"}",                                 // sifre yok
            "{\"email\": \"ayse@example.com\", \"fullName\": \"Ayse\", \"password\": \"1234567\"}",      // 7 karakter
            "{\"email\": \"ayse@example.com\", \"fullName\": \"Ayse\", \"password\": \"%2$s\"}"          // 73 karakter
    })
    void invalidFieldsReturn400AndNothingIsStored(String body) throws Exception {
        int before = userCount();

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("x".repeat(101), "p".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void secondRegistrationWithSameEmailInDifferentCaseReturns409() throws Exception {
        register("mehmet@example.com", "Mehmet Kaya", PASSWORD).andExpect(status().isCreated());

        register("MEHMET@Example.COM", "Baska Biri", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Email already registered"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email = 'mehmet@example.com'", Integer.class)).isEqualTo(1);
    }

    /**
     * Ayni anda gelen iki istegi taklit eder: Java on kontrolu "yok" der,
     * ama veritabanindaki UNIQUE kisit (uk_app_user_email) ikinci kaydi reddeder -> yine 409.
     */
    @Test
    void databaseUniqueConstraintAlsoReturns409WhenJavaCheckIsBypassed() throws Exception {
        jdbc.update("INSERT INTO app_user (email, full_name) VALUES ('ZEYNEP@EXAMPLE.COM', 'Zeynep Demir')");
        doReturn(false).when(appUserRepository).existsByEmail(anyString());

        register("zeynep@example.com", "Zeynep Demir", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        verify(appUserRepository).existsByEmail("zeynep@example.com");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email = 'zeynep@example.com'", Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------ GET /api/users/{id} ve /me

    @Test
    void locationFromRegistrationReturnsTheUserToThemself() throws Exception {
        String location = register("elif@example.com", "Elif Sahin", PASSWORD)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        long id = lastInsertedId();

        mockMvc.perform(get(location).with(asUser(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("elif@example.com"));
        mockMvc.perform(get("/api/users/me").with(asUser(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void userCannotReadAnotherUserButAdminCan() throws Exception {
        register("gizli@example.com", "Gizli", PASSWORD).andExpect(status().isCreated());
        long target = lastInsertedId();

        mockMvc.perform(get("/api/users/{id}", target).with(asUser(target + 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("gizli@example.com"))));
        mockMvc.perform(get("/api/users/{id}", target).with(asAdmin(target + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gizli@example.com"));
        mockMvc.perform(get("/api/users/{id}", target))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getUnknownUserReturns404ForAdmin() throws Exception {
        long missingId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM app_user", Long.class);

        mockMvc.perform(get("/api/users/{id}", missingId).with(asAdmin(1)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("User " + missingId + " was not found"));
    }

    // ------------------------------------------------------------ yardimcilar

    private ResultActions register(String email, String fullName, String password) throws Exception {
        return mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "fullName": "%s", "password": "%s"}
                """.formatted(email, fullName, password)));
    }

    private long lastInsertedId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM app_user", Long.class);
    }

    private int userCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
    }
}
