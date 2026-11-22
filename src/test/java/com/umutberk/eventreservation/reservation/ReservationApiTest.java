package com.umutberk.eventreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rezervasyon API'sinin tek istekteki davranislari, gercek MySQL test veritabaninda.
 * Her test sonunda geri alinir. Eszamanlilik icin bkz. ReservationConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class ReservationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private ReservationRepository reservationRepository;

    @Test
    void createReturns201WithLocationThatResolves() throws Exception {
        long eventId = createEvent(3);
        long userId = createUser();

        String location = reserve(eventId, userId)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/reservations/" + lastReservationId())))
                .andExpect(jsonPath("$.id").value(lastReservationId()))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.userId").value(userId));
        assertThat(confirmedCount(eventId)).isEqualTo(1);
    }

    @Test
    void unknownEventReturns404() throws Exception {
        long userId = createUser();
        long missingEventId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM event", Long.class);

        reserve(missingEventId, userId)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        long eventId = createEvent(3);
        long missingUserId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM app_user", Long.class);

        reserve(eventId, missingUserId)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        assertThat(confirmedCount(eventId)).isZero();
    }

    @Test
    void secondReservationBySameUserReturns409AlreadyReserved() throws Exception {
        long eventId = createEvent(3);
        long userId = createUser();
        reserve(eventId, userId).andExpect(status().isCreated());

        reserve(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Already reserved"))
                .andExpect(jsonPath("$.code").value("ALREADY_RESERVED"));
        assertThat(confirmedCount(eventId)).isEqualTo(1);
    }

    @Test
    void fullEventReturns409EventFull() throws Exception {
        long eventId = createEvent(2);
        reserve(eventId, createUser()).andExpect(status().isCreated());
        reserve(eventId, createUser()).andExpect(status().isCreated());

        reserve(eventId, createUser())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Event is full"))
                .andExpect(jsonPath("$.code").value("EVENT_FULL"));
        assertThat(confirmedCount(eventId)).isEqualTo(2);
    }

    @Test
    void cancelledReservationsDoNotUseCapacity() throws Exception {
        long eventId = createEvent(1);
        jdbc.update("INSERT INTO reservation (event_id, user_id, status) VALUES (?, ?, 'CANCELLED')", eventId, createUser());

        reserve(eventId, createUser()).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"userId\": null}", "{\"userId\": 0}", "{\"userId\": -5}", "{\"userId\": \"abc\"}", "not json"})
    void invalidBodyReturns400(String body) throws Exception {
        long eventId = createEvent(3);

        mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(confirmedCount(eventId)).isZero();
    }

    @Test
    void unknownReservationReturns404() throws Exception {
        long missingId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM reservation", Long.class);

        mockMvc.perform(get("/api/reservations/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    /**
     * Java on kontrolu bilerek atlanir; mukerrer kaydi yalnizca veritabanindaki
     * uk_reservation_active_user_event kisiti engelleyebilir -> yine 409 ALREADY_RESERVED.
     */
    @Test
    void databaseConstraintAlsoReturns409WhenJavaCheckIsBypassed() throws Exception {
        long eventId = createEvent(5);
        long userId = createUser();
        jdbc.update("INSERT INTO reservation (event_id, user_id, status) VALUES (?, ?, 'CONFIRMED')", eventId, userId);
        doReturn(false).when(reservationRepository).existsByEventIdAndUserIdAndStatus(anyLong(), anyLong(), any());

        reserve(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESERVED"));

        verify(reservationRepository).existsByEventIdAndUserIdAndStatus(eventId, userId, ReservationStatus.CONFIRMED);
        assertThat(confirmedCount(eventId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- yardimcilar

    private ResultActions reserve(long eventId, long userId) throws Exception {
        return mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": " + userId + "}"));
    }

    private long createEvent(int capacity) {
        jdbc.update("INSERT INTO event (name, starts_at, capacity) VALUES ('Rezervasyon testi', '2027-05-01 18:00:00', ?)",
                capacity);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long createUser() {
        jdbc.update("INSERT INTO app_user (email, full_name) VALUES (?, 'Rezervasyon Testi')",
                "reservation-" + UUID.randomUUID() + "@example.com");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long lastReservationId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM reservation", Long.class);
    }

    private long confirmedCount(long eventId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE event_id = ? AND status = 'CONFIRMED'", Long.class, eventId);
    }
}
