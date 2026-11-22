package com.umutberk.eventreservation.support;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.testcontainers.mysql.MySQLContainer;

/**
 * "test" profilindeki her uygulama baglami icin veritabanini secer (META-INF/spring.factories ile kayitli):
 *
 * 1. DB_TEST_URL tanimliysa o veritabani kullanilir (ornegin yerel MySQL; DB_PASSWORD de gerekir).
 * 2. Tanimli degilse Testcontainers ile gecici bir MySQL 8.4 konteyneri baslatilir (Docker gerekir).
 *    Konteyner JVM basina BIR kez baslar ve butun test baglamlari onu paylasir; testler bitince silinir.
 *
 * Ikisi de yoksa testler acik bir hatayla basarisiz olur; sessizce atlanmaz.
 */
public class TestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String MYSQL_IMAGE = "mysql:8.4";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        if (!env.acceptsProfiles(Profiles.of("test"))) {
            return;
        }
        String externalUrl = env.getProperty("DB_TEST_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            return; // application-test.properties DB_TEST_URL'i zaten kullanir
        }
        MySQLContainer mysql = SharedMySql.get();
        env.getPropertySources().addFirst(new MapPropertySource("testcontainers-mysql", Map.of(
                "spring.datasource.url", mysql.getJdbcUrl(),
                "spring.datasource.username", mysql.getUsername(),
                "spring.datasource.password", mysql.getPassword())));
    }

    /** Tek, paylasilan konteyner (ilk ihtiyacta baslar). */
    static final class SharedMySql {

        private static MySQLContainer container;

        private SharedMySql() {
        }

        static synchronized MySQLContainer get() {
            if (container == null) {
                MySQLContainer mysql = new MySQLContainer(MYSQL_IMAGE)
                        .withDatabaseName("reservation_platform_test")
                        .withUsername("reservation_app")
                        .withPassword("tc-" + UUID.randomUUID()); // her calistirmada rastgele, hicbir yere yazilmaz
                mysql.start();
                container = mysql;
            }
            return container;
        }
    }
}
