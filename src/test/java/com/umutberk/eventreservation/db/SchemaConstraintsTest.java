package com.umutberk.eventreservation.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
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

    private int insertEvent(String name, int capacity) {
        return jdbc.update(
                "INSERT INTO event (name, starts_at, capacity) VALUES (?, '2027-01-15 19:00:00', ?)",
                name, capacity);
    }

    private int insertUser(String email, String fullName) {
        return jdbc.update("INSERT INTO app_user (email, full_name) VALUES (?, ?)", email, fullName);
    }
}
