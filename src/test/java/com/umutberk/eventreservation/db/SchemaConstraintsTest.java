package com.umutberk.eventreservation.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Veritabani kurallarinin (CHECK / UNIQUE) MySQL tarafinda gercekten uygulandigini dogrular.
 * Her test @Transactional oldugu icin sonunda geri alinir; veritabaninda veri kalmaz.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaConstraintsTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void eventWithPositiveCapacityIsAccepted() {
        int inserted = insertEvent("Java Meetup", 1);
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void eventCapacityMustBePositive() {
        assertThatThrownBy(() -> insertEvent("Bos etkinlik", 0))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_event_capacity_positive");
        assertThatThrownBy(() -> insertEvent("Negatif etkinlik", -5))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_event_capacity_positive");
    }

    @Test
    void eventNameMustNotBeBlank() {
        assertThatThrownBy(() -> insertEvent("   ", 10))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_event_name_not_blank");
    }

    @Test
    void userEmailIsUniqueIgnoringCase() {
        insertUser("ayse@example.com", "Ayse Yilmaz");
        assertThatThrownBy(() -> insertUser("AYSE@example.com", "Ayse Baska"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_app_user_email");
    }

    // ---------- V2: reservation ----------

    @Test
    void reservationRequiresExistingEventAndUser() {
        insertEvent("FK Etkinlik", 5);
        long eventId = lastId();
        insertUser("fk@example.com", "FK Kullanici");
        long userId = lastId();

        assertThatThrownBy(() -> insertReservation(eventId + 100_000, userId, "CONFIRMED"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_reservation_event");
        assertThatThrownBy(() -> insertReservation(eventId, userId + 100_000, "CONFIRMED"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_reservation_user");
    }

    @Test
    void sameUserCannotHaveTwoActiveReservationsForSameEvent() {
        insertEvent("Tekil Etkinlik", 5);
        long eventId = lastId();
        insertUser("tekil@example.com", "Tekil Kullanici");
        long userId = lastId();

        insertReservation(eventId, userId, "CONFIRMED");
        assertThatThrownBy(() -> insertReservation(eventId, userId, "CONFIRMED"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_reservation_active_user_event");
    }

    @Test
    void cancelledReservationsDoNotBlockANewActiveOne() {
        insertEvent("Iptal Etkinlik", 5);
        long eventId = lastId();
        insertUser("iptal@example.com", "Iptal Kullanici");
        long userId = lastId();

        insertReservation(eventId, userId, "CANCELLED");
        insertReservation(eventId, userId, "CANCELLED");
        assertThat(insertReservation(eventId, userId, "CONFIRMED")).isEqualTo(1);
    }

    @Test
    void reservationStatusMustBeKnown() {
        insertEvent("Durum Etkinlik", 5);
        long eventId = lastId();
        insertUser("durum@example.com", "Durum Kullanici");
        long userId = lastId();

        assertThatThrownBy(() -> insertReservation(eventId, userId, "PENDING"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_reservation_status");
    }

    // ---------- V3: bekleme listesi ----------

    @Test
    void userCannotBeConfirmedAndWaitlistedForSameEvent() {
        long[] ids = eventAndUser("v3-both@example.com");
        insertReservation(ids[0], ids[1], "CONFIRMED");

        assertThatThrownBy(() -> insertWaitlisted(ids[0], ids[1], 1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_reservation_active_user_event");
    }

    @Test
    void userCannotEnterWaitlistTwice() {
        long[] ids = eventAndUser("v3-twice@example.com");
        insertWaitlisted(ids[0], ids[1], 1);

        assertThatThrownBy(() -> insertWaitlisted(ids[0], ids[1], 2))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_reservation_active_user_event");
    }

    @Test
    void waitlistNumberIsUniquePerEvent() {
        long[] ids = eventAndUser("v3-number-a@example.com");
        insertUser("v3-number-b@example.com", "B");
        long otherUser = lastId();
        insertWaitlisted(ids[0], ids[1], 1);

        assertThatThrownBy(() -> insertWaitlisted(ids[0], otherUser, 1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_reservation_event_waitlist_number");
    }

    @Test
    void waitlistedRowNeedsAPositiveNumber() {
        long[] ids = eventAndUser("v3-null@example.com");

        assertThatThrownBy(() -> insertReservation(ids[0], ids[1], "WAITLISTED"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_reservation_waitlisted_has_number");
        assertThatThrownBy(() -> insertWaitlisted(ids[0], ids[1], 0))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_reservation_waitlist_number_positive");
    }

    @Test
    void cancelledRowDoesNotBlockNewWaitlistEntry() {
        long[] ids = eventAndUser("v3-cancel@example.com");
        jdbc.update("INSERT INTO reservation (event_id, user_id, status, waitlist_number) VALUES (?, ?, 'CANCELLED', 1)",
                ids[0], ids[1]);

        assertThat(insertWaitlisted(ids[0], ids[1], 2)).isEqualTo(1);
    }

    // ---------- V4: rol ----------

    @Test
    void newUserDefaultsToUserRoleAndRoleMustBeKnown() {
        insertUser("v4-role@example.com", "V4");
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id = ?", String.class, lastId())).isEqualTo("USER");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_user (email, full_name, role) VALUES ('v4-root@example.com', 'Root', 'ROOT')"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_app_user_role");
    }

    private long[] eventAndUser(String email) {
        insertEvent("V3 Etkinlik", 1);
        long eventId = lastId();
        insertUser(email, "V3 Kullanici");
        return new long[] {eventId, lastId()};
    }

    private int insertWaitlisted(long eventId, long userId, int number) {
        return jdbc.update(
                "INSERT INTO reservation (event_id, user_id, status, waitlist_number) VALUES (?, ?, 'WAITLISTED', ?)",
                eventId, userId, number);
    }

    private int insertEvent(String name, int capacity) {
        return jdbc.update(
                "INSERT INTO event (name, starts_at, capacity) VALUES (?, '2027-01-15 19:00:00', ?)",
                name, capacity);
    }

    private int insertUser(String email, String fullName) {
        return jdbc.update("INSERT INTO app_user (email, full_name) VALUES (?, ?)", email, fullName);
    }

    private int insertReservation(long eventId, long userId, String status) {
        return jdbc.update(
                "INSERT INTO reservation (event_id, user_id, status) VALUES (?, ?, ?)", eventId, userId, status);
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
