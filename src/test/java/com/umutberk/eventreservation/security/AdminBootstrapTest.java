package com.umutberk.eventreservation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * ADMIN_EMAIL / ADMIN_PASSWORD verilince acilista ADMIN olusturulur; bu admin gercek giris yapip
 * yalnizca ADMIN'e acik etkinlik olusturma ucunu kullanabilir.
 * Sifre yalnizca bu teste ait sahte bir degerdir. Test, olusturdugu kayitlari sonunda siler (yalnizca test DB).
 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.email=bootstrap-admin@example.com",
        "app.bootstrap-admin.password=test-only-admin-password"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class AdminBootstrapTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bootstrapAdminCanLogInAndCreateEvents() throws Exception {
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE email = 'bootstrap-admin@example.com'",
                String.class)).isEqualTo("ADMIN");

        String body = mockMvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"bootstrap-admin@example.com\", \"password\": \"test-only-admin-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(post("/api/events").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bootstrap Admin Etkinligi\", \"startsAt\": \"2027-03-01T10:00:00Z\", \"capacity\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bootstrap Admin Etkinligi"));
    }

    @AfterEach
    void cleanUp() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbc.update("DELETE FROM event WHERE name = 'Bootstrap Admin Etkinligi'");
        jdbc.update("DELETE FROM app_user WHERE email = 'bootstrap-admin@example.com'");
    }
}
