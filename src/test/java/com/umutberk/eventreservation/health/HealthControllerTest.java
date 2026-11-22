package com.umutberk.eventreservation.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.umutberk.eventreservation.security.SecurityConfig;
import com.umutberk.eventreservation.security.WebSecurityConfig;

/**
 * Sadece web katmanini (controller) ayaga kaldirip HTTP istegini taklit eder.
 * Gercek bir sunucu baslatmaz, bu yuzden hizlidir.
 */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, WebSecurityConfig.class}) // gercek guvenlik kurallari: /api/health herkese acik olmali
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.app").value("event-reservation"));
    }
}
