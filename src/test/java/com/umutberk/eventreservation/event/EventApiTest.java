package com.umutberk.eventreservation.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.umutberk.eventreservation.support.TestAuth.asAdmin;
import static com.umutberk.eventreservation.support.TestAuth.asUser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Etkinlik API'sini gercek MySQL test veritabanina karsi uctan uca dener
 * (HTTP -> controller -> service -> repository -> MySQL).
 * Her test sonunda islem geri alinir; test verisi kalmaz, gelistirme veritabanina dokunulmaz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class EventApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createReturns201WithLocationAndStoresEvent() throws Exception {
        String location = mockMvc.perform(post("/api/events").with(asAdmin(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Spring Boot Workshop ", "startsAt": "2027-01-15T19:00:00Z", "capacity": 30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/events/" + lastInsertedId())))
                .andExpect(jsonPath("$.id").value(lastInsertedId()))
                .andExpect(jsonPath("$.name").value("Spring Boot Workshop"))
                .andExpect(jsonPath("$.startsAt").value("2027-01-15T19:00:00Z"))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andReturn().getResponse().getHeader("Location");

        // Veritabaninda UTC olarak saklandi mi?
        String storedStartsAt = jdbc.queryForObject(
                "SELECT DATE_FORMAT(starts_at, '%Y-%m-%d %H:%i:%s') FROM event WHERE id = ?",
                String.class, lastInsertedId());
        assertThat(storedStartsAt).isEqualTo("2027-01-15 19:00:00");

        // created_at'i MySQL uretir; o da UTC olmali (gercek UTC saatinden en fazla 1 dk fark).
        Long secondsFromUtcNow = jdbc.queryForObject(
                "SELECT ABS(TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6))) FROM event WHERE id = ?",
                Long.class, lastInsertedId());
        assertThat(secondsFromUtcNow).isLessThan(60);

        // Location adresi gercekten yeni kaydi gosteriyor mu?
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spring Boot Workshop"));
    }

    @Test
    void listReturnsAllEventsOrderedByStartTime() throws Exception {
        createEvent("Gec Etkinlik", "2027-03-01T10:00:00Z", 5);
        createEvent("Erken Etkinlik", "2027-02-01T10:00:00Z", 5);
        int rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class);

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(rowCount)))
                .andExpect(jsonPath("$[*].name", containsInRelativeOrder("Erken Etkinlik", "Gec Etkinlik")));
    }

    @Test
    void getByIdReturnsEventDetails() throws Exception {
        createEvent("Java Meetup", "2027-04-10T17:30:00Z", 50);
        long id = lastInsertedId();

        mockMvc.perform(get("/api/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Java Meetup"))
                .andExpect(jsonPath("$.startsAt").value("2027-04-10T17:30:00Z"))
                .andExpect(jsonPath("$.capacity").value(50));
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        long missingId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM event", Long.class);

        mockMvc.perform(get("/api/events/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Event not found"))
                .andExpect(jsonPath("$.detail").value("Event " + missingId + " was not found"));
    }

    @Test
    void createRejectsInvalidRequestWith400() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class);

        mockMvc.perform(post("/api/events").with(asAdmin(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "startsAt": "2027-01-15T19:00:00Z", "capacity": 0}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class)).isEqualTo(before);
    }

    // ------------------------------------------------------------ yetki

    @Test
    void creatingEventWithoutTokenReturns401() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class);

        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"X\", \"startsAt\": \"2027-01-15T19:00:00Z\", \"capacity\": 3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class)).isEqualTo(before);
    }

    @Test
    void creatingEventAsNormalUserReturns403() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class);

        mockMvc.perform(post("/api/events").with(asUser(1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"X\", \"startsAt\": \"2027-01-15T19:00:00Z\", \"capacity\": 3}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event", Integer.class)).isEqualTo(before);
    }

    @Test
    void listingAndDetailArePublic() throws Exception {
        createEvent("Herkese Acik", "2027-07-01T10:00:00Z", 10);

        mockMvc.perform(get("/api/events")).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/{id}", lastInsertedId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Herkese Acik"));
    }

    private void createEvent(String name, String startsAt, int capacity) throws Exception {
        mockMvc.perform(post("/api/events").with(asAdmin(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "startsAt": "%s", "capacity": %d}
                                """.formatted(name, startsAt, capacity)))
                .andExpect(status().isCreated());
    }

    private long lastInsertedId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM event", Long.class);
    }
}
