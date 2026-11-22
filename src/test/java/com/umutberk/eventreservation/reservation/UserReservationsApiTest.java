package com.umutberk.eventreservation.reservation;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** GET /api/users/{userId}/reservations: kullanicinin kendi kayitlari, filtre ve sayfalama. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class UserReservationsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listsOwnReservationsNewestFirstWithEventSummaryAndPosition() throws Exception {
        long user = createUser();
        long other = createUser();
        long open = createEvent("Acik Etkinlik", 5);
        long full = createEvent("Dolu Etkinlik", 1);
        reserve(full, createUser());            // dolu etkinligi baskasi doldurur
        reserve(full, createUser());            // baskasi siranin 1. olur
        long confirmedId = idOf(reserve(open, user));
        long waitingId = idOf(reserve(full, user));
        reserve(open, other);                   // baska kullanicinin kaydi listeye girmemeli

        list(user, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[*].id", contains((int) waitingId, (int) confirmedId)))
                .andExpect(jsonPath("$.content[0].status").value("WAITLISTED"))
                .andExpect(jsonPath("$.content[0].waitlistNumber").value(2))
                .andExpect(jsonPath("$.content[0].waitlistPosition").value(2))
                .andExpect(jsonPath("$.content[0].event.id").value(full))
                .andExpect(jsonPath("$.content[0].event.name").value("Dolu Etkinlik"))
                .andExpect(jsonPath("$.content[0].event.startsAt").value("2027-05-01T18:00:00Z"))
                .andExpect(jsonPath("$.content[1].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[1].waitlistPosition").value(nullValue()))
                .andExpect(jsonPath("$.content[1].event.name").value("Acik Etkinlik"));
    }

    @Test
    void filtersByStatusIncludingCancelled() throws Exception {
        long user = createUser();
        long a = createEvent("A", 5);
        long b = createEvent("B", 5);
        long cancelledId = idOf(reserve(a, user));
        mockMvc.perform(post("/api/reservations/{id}/cancel", cancelledId)).andExpect(status().isOk());
        reserve(b, user);

        list(user, "?status=CANCELLED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cancelledId))
                .andExpect(jsonPath("$.content[0].cancelledAt").exists());
        list(user, "?status=CONFIRMED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].event.name").value("B"));
        list(user, "?status=WAITLISTED")
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void paginatesWithStableOrder() throws Exception {
        long user = createUser();
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = idOf(reserve(createEvent("E" + i, 5), user));
        }

        list(user, "?page=0&size=2")
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[*].id", contains((int) ids[4], (int) ids[3])));
        list(user, "?page=2&size=2")
                .andExpect(jsonPath("$.content[*].id", contains((int) ids[0])));
        list(user, "?page=3&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void userWithoutReservationsGetsEmptyPage() throws Exception {
        list(createUser(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        long missing = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM app_user", Long.class);

        list(missing, "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?size=abc", "?status=PENDING"})
    void invalidParametersReturn400(String query) throws Exception {
        list(createUser(), query)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ------------------------------------------------------------ yardimcilar

    private ResultActions list(long userId, String query) throws Exception {
        return mockMvc.perform(get("/api/users/" + userId + "/reservations" + query));
    }

    private ResultActions reserve(long eventId, long userId) throws Exception {
        return mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": " + userId + "}"));
    }

    private static long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createEvent(String name, int capacity) {
        jdbc.update("INSERT INTO event (name, starts_at, capacity) VALUES (?, '2027-05-01 18:00:00', ?)", name, capacity);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long createUser() {
        jdbc.update("INSERT INTO app_user (email, full_name) VALUES (?, 'Liste Testi')",
                "list-" + UUID.randomUUID() + "@example.com");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
