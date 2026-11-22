package com.umutberk.eventreservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Gercek MySQL'e baglanip dogru (test) veritabanina, dogru kullaniciyla
 * baglandigimizi kontrol eder (DB_TEST_URL ya da Testcontainers).
 */
@SpringBootTest
@ActiveProfiles("test")
class MySqlConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToApplicationDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        String user = jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class);
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        assertThat(database).isEqualTo("reservation_platform_test");
        // Yerel MySQL'de "reservation_app@localhost", konteynerde "reservation_app@%"
        assertThat(user).startsWith("reservation_app@");
        assertThat(one).isEqualTo(1);
    }
}
