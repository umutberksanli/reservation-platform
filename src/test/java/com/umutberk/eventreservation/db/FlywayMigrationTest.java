package com.umutberk.eventreservation.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.umutberk.eventreservation.EventReservationApplication;

/**
 * Uygulamayi gercek MySQL test veritabanina karsi iki kez baslatir:
 * 1. acilis: bos veritabaninda V1 migration'i otomatik uygulanir, tablolar olusur.
 * 2. acilis: migration TEKRAR uygulanmaz (gecmis tablosu degismez).
 */
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class FlywayMigrationTest {

    @Test
    void migrationRunsOnFirstStartupAndIsNotReappliedOnSecondStartup() {
        emptyTestDatabase();

        // --- 1. acilis ---
        List<Map<String, Object>> historyAfterFirstStart;
        try (ConfigurableApplicationContext app = startApplication()) {
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);

            historyAfterFirstStart = appliedMigrations(jdbc);
            assertThat(historyAfterFirstStart).hasSize(1);
            assertThat(historyAfterFirstStart.get(0))
                    .containsEntry("version", "1")
                    .containsEntry("description", "create event and app user")
                    .containsEntry("success", true);

            assertThat(tableNames(jdbc)).contains("event", "app_user");
            assertThat(columnNames(jdbc, "event"))
                    .containsExactly("id", "name", "starts_at", "capacity", "created_at");
            assertThat(columnNames(jdbc, "app_user"))
                    .containsExactly("id", "email", "full_name", "created_at");
        }

        // --- 2. acilis ---
        try (ConfigurableApplicationContext app = startApplication()) {
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
            Flyway flyway = app.getBean(Flyway.class);

            List<Map<String, Object>> historyAfterSecondStart = appliedMigrations(jdbc);
            // Ayni satir, ayni uygulanma zamani: migration yeniden calismadi.
            assertThat(historyAfterSecondStart).isEqualTo(historyAfterFirstStart);
            assertThat(flyway.info().pending()).isEmpty();
        }
    }

    /** Uygulamayi "test" profiliyle, web sunucusu acmadan baslatir. */
    private static ConfigurableApplicationContext startApplication(String... extraProperties) {
        return new SpringApplicationBuilder(EventReservationApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties(extraProperties)
                .run();
    }

    /**
     * Test veritabanindaki tum tablolari siler. Baglanti ayarlarini uygulamanin
     * kendisinden almak icin Flyway kapali bir acilis yapilir.
     */
    private static void emptyTestDatabase() {
        try (ConfigurableApplicationContext app = startApplication("spring.flyway.enabled=false")) {
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
                    .as("guvenlik: yalnizca test veritabani temizlenebilir")
                    .endsWith("_test");

            Flyway.configure()
                    .dataSource(app.getBean(DataSource.class))
                    .cleanDisabled(false)
                    .load()
                    .clean();

            assertThat(tableNames(jdbc)).isEmpty();
        }
    }

    private static List<Map<String, Object>> appliedMigrations(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT installed_rank, version, description, checksum, installed_on, success
                FROM flyway_schema_history
                WHERE type = 'SQL'
                ORDER BY installed_rank
                """).stream()
                .map(row -> {
                    // MySQL'de success TINYINT(1); surucuye gore Boolean ya da sayi gelebilir.
                    Object success = row.get("success");
                    row.put("success", Boolean.TRUE.equals(success) || Integer.valueOf(1).equals(success));
                    return row;
                })
                .toList();
    }

    private static List<String> tableNames(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() ORDER BY table_name
                """, String.class);
    }

    private static List<String> columnNames(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                ORDER BY ordinal_position
                """, String.class, table);
    }
}
