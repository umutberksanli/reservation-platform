package com.umutberk.eventreservation.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/users davranisini gercek MySQL test veritabanina karsi dener.
 * Her test sonunda islem geri alinir; gelistirme veritabanina dokunulmaz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class UserRegistrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Gercek repository; yalnizca bir testte on kontrolu devre disi birakmak icin sarmalanir. */
    @MockitoSpyBean
    private AppUserRepository appUserRepository;

    @Test
    void validRegistrationReturns201WithLocation() throws Exception {
        register("""
                {"email": "  Ayse.Yilmaz@Example.com ", "fullName": "  Ayse Yilmaz "}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/users/" + lastInsertedId())))
                .andExpect(jsonPath("$.id").value(lastInsertedId()))
                .andExpect(jsonPath("$.email").value("ayse.yilmaz@example.com"))
                .andExpect(jsonPath("$.fullName").value("Ayse Yilmaz"))
                .andExpect(jsonPath("$.createdAt").exists());

        String stored = jdbc.queryForObject(
                "SELECT CONCAT(email, '|', full_name) FROM app_user WHERE id = ?", String.class, lastInsertedId());
        assertThat(stored).isEqualTo("ayse.yilmaz@example.com|Ayse Yilmaz");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\": \"\", \"fullName\": \"Ayse Yilmaz\"}",                // bos e-posta
            "{\"email\": \"   \", \"fullName\": \"Ayse Yilmaz\"}",             // sadece bosluk
            "{\"email\": \"not-an-email\", \"fullName\": \"Ayse Yilmaz\"}",    // gecersiz bicim
            "{\"fullName\": \"Ayse Yilmaz\"}",                                 // e-posta yok
            "{\"email\": \"ayse@example.com\", \"fullName\": \"\"}",           // bos ad
            "{\"email\": \"ayse@example.com\", \"fullName\": \"   \"}",        // sadece bosluk
            "{\"email\": \"ayse@example.com\"}",                               // ad yok
            "{\"email\": \"ayse@example.com\", \"fullName\": \"%s\"}"          // 101 karakterlik ad
    })
    void invalidFieldsReturn400AndNothingIsStored(String body) throws Exception {
        int before = userCount();

        register(body.formatted("x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void secondRegistrationWithSameEmailInDifferentCaseReturns409() throws Exception {
        register("""
                {"email": "mehmet@example.com", "fullName": "Mehmet Kaya"}
                """).andExpect(status().isCreated());

        register("""
                {"email": "MEHMET@Example.COM", "fullName": "Baska Biri"}
                """)
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
     * Satir buyuk harfle, uygulamayi atlayarak dogrudan SQL ile eklenir; kisit buyuk/kucuk harf duyarsizdir.
     */
    @Test
    void databaseUniqueConstraintAlsoReturns409WhenJavaCheckIsBypassed() throws Exception {
        jdbc.update("INSERT INTO app_user (email, full_name) VALUES ('ZEYNEP@EXAMPLE.COM', 'Zeynep Demir')");
        doReturn(false).when(appUserRepository).existsByEmail(anyString());

        register("""
                {"email": "zeynep@example.com", "fullName": "Zeynep Demir"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        verify(appUserRepository).existsByEmail("zeynep@example.com"); // on kontrol gercekten atlandi
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email = 'zeynep@example.com'", Integer.class)).isEqualTo(1);
    }

    @Test
    void locationFromRegistrationReturnsTheUser() throws Exception {
        String location = register("""
                {"email": "elif@example.com", "fullName": "Elif Sahin"}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(lastInsertedId()))
                .andExpect(jsonPath("$.email").value("elif@example.com"))
                .andExpect(jsonPath("$.fullName").value("Elif Sahin"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void getUnknownUserReturns404() throws Exception {
        long missingId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM app_user", Long.class);

        mockMvc.perform(get("/api/users/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("User " + missingId + " was not found"));
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long lastInsertedId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM app_user", Long.class);
    }

    private int userCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class);
    }
}
