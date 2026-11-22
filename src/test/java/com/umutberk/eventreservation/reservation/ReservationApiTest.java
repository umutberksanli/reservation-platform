package com.umutberk.eventreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import com.jayway.jsonpath.JsonPath;

/**
 * Rezervasyon + bekleme listesi + iptal davranislari, tek tek istekler halinde,
 * gercek MySQL test veritabaninda. Her test sonunda geri alinir.
 * Her senaryonun sonunda WaitlistInvariants ile kapasite ve FIFO kurallari dogrulanir.
 * Eszamanlilik icin bkz. ReservationConcurrencyTest.
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

    private Long scenarioEventId;

    @AfterEach
    void invariantsHold() {
        if (scenarioEventId != null) {
            WaitlistInvariants.assertHold(jdbc, scenarioEventId);
        }
    }

    // ------------------------------------------------------------ olusturma

    @Test
    void createReturns201WithLocationThatResolves() throws Exception {
        long eventId = createEvent(3);
        long userId = createUser();

        String location = reserve(eventId, userId)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/reservations/" + lastReservationId())))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.waitlistNumber").value(nullValue()))
                .andExpect(jsonPath("$.waitlistPosition").value(nullValue()))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
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
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        assertThat(WaitlistInvariants.count(jdbc, eventId, "CONFIRMED")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"userId\": null}", "{\"userId\": 0}", "{\"userId\": -5}", "{\"userId\": \"abc\"}", "not json"})
    void invalidBodyReturns400(String body) throws Exception {
        long eventId = createEvent(3);

        mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(WaitlistInvariants.count(jdbc, eventId, "CONFIRMED")).isZero();
    }

    // ------------------------------------------------------------ bekleme listesi (FIFO)

    @Test
    void fullEventPutsUsersOnWaitlistInArrivalOrderWith202() throws Exception {
        long eventId = createEvent(1);
        reserve(eventId, createUser()).andExpect(status().isCreated());

        long second = createUser();
        long third = createUser();
        reserve(eventId, second)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/reservations/" + lastReservationId())))
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.waitlistNumber").value(1))
                .andExpect(jsonPath("$.waitlistPosition").value(1));
        reserve(eventId, third)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.waitlistNumber").value(2))
                .andExpect(jsonPath("$.waitlistPosition").value(2));

        assertThat(WaitlistInvariants.waitingUsersInOrder(jdbc, eventId)).containsExactly(second, third);
    }

    @Test
    void confirmedUserCannotAlsoJoinWaitlist() throws Exception {
        long eventId = createEvent(1);
        long userId = createUser();
        reserve(eventId, userId).andExpect(status().isCreated());

        reserve(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESERVED"))
                .andExpect(jsonPath("$.currentStatus").value("CONFIRMED"));
        assertThat(WaitlistInvariants.count(jdbc, eventId, "WAITLISTED")).isZero();
    }

    @Test
    void waitlistedUserCannotJoinTwice() throws Exception {
        long eventId = createEvent(1);
        reserve(eventId, createUser()).andExpect(status().isCreated());
        long userId = createUser();
        reserve(eventId, userId).andExpect(status().isAccepted());

        reserve(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESERVED"))
                .andExpect(jsonPath("$.currentStatus").value("WAITLISTED"));
        assertThat(WaitlistInvariants.waitingNumbers(jdbc, eventId)).containsExactly(1);
    }

    @Test
    void statusEndpointShowsCurrentPosition() throws Exception {
        long eventId = createEvent(1);
        long confirmedUser = createUser();
        long first = createUser();
        long second = createUser();
        long stranger = createUser();
        reserve(eventId, confirmedUser);
        reserve(eventId, first);
        reserve(eventId, second);

        statusOf(eventId, confirmedUser).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.waitlistPosition").value(nullValue()));
        statusOf(eventId, second).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.waitlistPosition").value(2));
        statusOf(eventId, stranger).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_RESERVATION"));
        statusOf(eventId + 100_000, first).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    // ------------------------------------------------------------ iptal ve terfi

    @Test
    void cancellingConfirmedPromotesFirstWaiterInSameRequest() throws Exception {
        long eventId = createEvent(1);
        long confirmedUser = createUser();
        long first = createUser();
        long second = createUser();
        long confirmedId = idOf(reserve(eventId, confirmedUser));
        long firstId = idOf(reserve(eventId, first));
        reserve(eventId, second);

        cancel(confirmedId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyCancelled").value(false))
                .andExpect(jsonPath("$.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reservation.cancelledAt").exists())
                .andExpect(jsonPath("$.promoted", hasSize(1)))
                .andExpect(jsonPath("$.promoted[0].id").value(firstId))
                .andExpect(jsonPath("$.promoted[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.promoted[0].waitlistNumber").value(1))
                .andExpect(jsonPath("$.promoted[0].promotedAt").exists());

        assertThat(WaitlistInvariants.confirmedUsers(jdbc, eventId)).containsExactly(first);
        statusOf(eventId, second).andExpect(jsonPath("$.waitlistPosition").value(1));
    }

    @Test
    void cancellingWaitlistedLeavesQueueWithoutPromotion() throws Exception {
        long eventId = createEvent(1);
        long confirmedUser = createUser();
        long first = createUser();
        long second = createUser();
        reserve(eventId, confirmedUser);
        long firstId = idOf(reserve(eventId, first));
        reserve(eventId, second);

        cancel(firstId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.promoted", hasSize(0)));

        assertThat(WaitlistInvariants.confirmedUsers(jdbc, eventId)).containsExactly(confirmedUser);
        statusOf(eventId, second).andExpect(jsonPath("$.waitlistPosition").value(1))
                .andExpect(jsonPath("$.waitlistNumber").value(2)); // numara kalici, konum degisti
    }

    @Test
    void repeatedCancelIsIdempotentAndDoesNotPromoteTwice() throws Exception {
        long eventId = createEvent(1);
        long confirmedId = idOf(reserve(eventId, createUser()));
        long first = createUser();
        long second = createUser();
        reserve(eventId, first);
        reserve(eventId, second);

        cancel(confirmedId).andExpect(jsonPath("$.promoted", hasSize(1)));
        cancel(confirmedId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyCancelled").value(true))
                .andExpect(jsonPath("$.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.promoted", hasSize(0)));

        assertThat(WaitlistInvariants.confirmedUsers(jdbc, eventId)).containsExactly(first);
        assertThat(WaitlistInvariants.waitingUsersInOrder(jdbc, eventId)).containsExactly(second);
    }

    @Test
    void lastWaiterPromotedThenFreedSeatGoesToNextNewcomer() throws Exception {
        long eventId = createEvent(1);
        long aId = idOf(reserve(eventId, createUser()));
        long b = createUser();
        long bId = idOf(reserve(eventId, b));

        cancel(aId).andExpect(jsonPath("$.promoted[0].id").value(bId)); // son bekleyen terfi etti
        assertThat(WaitlistInvariants.count(jdbc, eventId, "WAITLISTED")).isZero();

        cancel(bId).andExpect(jsonPath("$.promoted", hasSize(0)));       // sira bos, yer bosta kalir
        assertThat(WaitlistInvariants.count(jdbc, eventId, "CONFIRMED")).isZero();

        reserve(eventId, createUser())
                .andExpect(status().isCreated())                         // yeni gelen dogrudan onaylanir
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void newcomerCannotTakeFreeSeatWhileSomeoneWaits() throws Exception {
        // Tutarsiz durumu dogrudan SQL ile kur: kapasite 2, 1 onayli, 1 bekleyen -> 1 bos yer var.
        long eventId = createEvent(2);
        long waiter = createUser();
        jdbc.update("INSERT INTO reservation (event_id, user_id, status) VALUES (?, ?, 'CONFIRMED')", eventId, createUser());
        jdbc.update("INSERT INTO reservation (event_id, user_id, status, waitlist_number) VALUES (?, ?, 'WAITLISTED', 1)",
                eventId, waiter);

        long newcomer = createUser();
        reserve(eventId, newcomer)
                .andExpect(status().isAccepted())                        // bos yer bekleyene verildi
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.waitlistNumber").value(2))
                .andExpect(jsonPath("$.waitlistPosition").value(1));

        assertThat(WaitlistInvariants.confirmedUsers(jdbc, eventId)).contains(waiter).doesNotContain(newcomer);
    }

    @Test
    void userCanRejoinAfterCancellingAndGoesToTheEnd() throws Exception {
        long eventId = createEvent(1);
        reserve(eventId, createUser());
        long a = createUser();
        long b = createUser();
        long aFirstId = idOf(reserve(eventId, a));
        reserve(eventId, b);

        cancel(aFirstId);
        reserve(eventId, a)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.waitlistNumber").value(3))
                .andExpect(jsonPath("$.waitlistPosition").value(2));

        assertThat(WaitlistInvariants.waitingUsersInOrder(jdbc, eventId)).containsExactly(b, a);
    }

    @Test
    void cancelledReservationsDoNotUseCapacity() throws Exception {
        long eventId = createEvent(1);
        jdbc.update("INSERT INTO reservation (event_id, user_id, status) VALUES (?, ?, 'CANCELLED')", eventId, createUser());

        reserve(eventId, createUser()).andExpect(status().isCreated());
    }

    @Test
    void unknownReservationReturns404OnGetAndCancel() throws Exception {
        long missingId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM reservation", Long.class);

        mockMvc.perform(get("/api/reservations/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        cancel(missingId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    /**
     * Java on kontrolu bilerek atlanir; mukerrer kaydi yalnizca veritabanindaki
     * uk_reservation_active_user_event kisiti engelleyebilir -> yine 409 ALREADY_RESERVED.
     */
    @Test
    void databaseConstraintAlsoReturns409WhenJavaCheckIsBypassed() throws Exception {
        long eventId = createEvent(1);
        jdbc.update("INSERT INTO reservation (event_id, user_id, status) VALUES (?, ?, 'CONFIRMED')", eventId, createUser());
        long userId = createUser();
        jdbc.update("INSERT INTO reservation (event_id, user_id, status, waitlist_number) VALUES (?, ?, 'WAITLISTED', 1)",
                eventId, userId);
        doReturn(Optional.empty()).when(reservationRepository).findActive(anyLong(), anyLong(), anyCollection());

        reserve(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESERVED"));

        verify(reservationRepository).findActive(eventId, userId, ReservationStatus.ACTIVE);
        assertThat(WaitlistInvariants.waitingNumbers(jdbc, eventId)).containsExactly(1);
    }

    // ------------------------------------------------------------ yardimcilar

    private ResultActions reserve(long eventId, long userId) throws Exception {
        return mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": " + userId + "}"));
    }

    private ResultActions cancel(long reservationId) throws Exception {
        return mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId));
    }

    private ResultActions statusOf(long eventId, long userId) throws Exception {
        return mockMvc.perform(get("/api/events/{eventId}/users/{userId}/reservation", eventId, userId));
    }

    private static long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createEvent(int capacity) {
        jdbc.update("INSERT INTO event (name, starts_at, capacity) VALUES ('Rezervasyon testi', '2027-05-01 18:00:00', ?)",
                capacity);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        scenarioEventId = id;
        return id;
    }

    private long createUser() {
        jdbc.update("INSERT INTO app_user (email, full_name) VALUES (?, 'Rezervasyon Testi')",
                "reservation-" + UUID.randomUUID() + "@example.com");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long lastReservationId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM reservation", Long.class);
    }
}
