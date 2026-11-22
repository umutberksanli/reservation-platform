package com.umutberk.eventreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Projenin kritik sarti: ayni anda gelen istekler kapasiteyi ASMAMALI.
 *
 * Gercek bir HTTP sunucusu (rastgele port) ve gercek MySQL test veritabani kullanilir.
 * Istekler bir "basla" sinyaliyle ayni anda gonderilir; her istek kendi thread'inde,
 * kendi transaction'inda calisir. Bu yuzden test @Transactional DEGILDIR; olusturdugu
 * kayitlari sonunda kendisi siler (yalnizca test veritabaninda, yalnizca kendi kayitlarini).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class ReservationConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 20;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final List<Long> createdEventIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    /** 20 farkli kullanici, kapasitesi 1 olan etkinlige ayni anda rezervasyon yapar -> tam 1 basarili. */
    @RepeatedTest(5)
    void concurrentRequestsNeverExceedCapacityOfOne() throws Exception {
        long eventId = createEvent(1);
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            userIds.add(createUser());
        }

        List<Result> results = sendSimultaneously(userIds.stream()
                .map(userId -> (Function<Void, Result>) unused -> reserve(eventId, userId))
                .toList());

        Map<String, Long> outcome = summarize(results);
        System.out.printf("[kapasite=1, %d istek] sonuc: %s, veritabanindaki aktif rezervasyon: %d%n",
                CONCURRENT_REQUESTS, outcome, confirmedCount(eventId));

        assertThat(confirmedCount(eventId))
                .as("veritabaninda tam olarak 1 aktif rezervasyon olmali")
                .isEqualTo(1);
        assertThat(outcome).containsEntry("201", 1L);
        assertThat(outcome).containsEntry("409 EVENT_FULL", (long) CONCURRENT_REQUESTS - 1);
    }

    /** Ayni kullanici ayni etkinlige ayni anda 10 istek gonderir -> tam 1 basarili, 9 ALREADY_RESERVED. */
    @Test
    void concurrentDuplicateRequestsFromSameUserCreateOnlyOneReservation() throws Exception {
        long eventId = createEvent(5);
        long userId = createUser();

        List<Result> results = sendSimultaneously(java.util.Collections.nCopies(10,
                (Function<Void, Result>) unused -> reserve(eventId, userId)));

        Map<String, Long> outcome = summarize(results);
        System.out.printf("[ayni kullanici, 10 istek] sonuc: %s%n", outcome);

        assertThat(confirmedCount(eventId)).isEqualTo(1);
        assertThat(outcome).containsEntry("201", 1L);
        assertThat(outcome).containsEntry("409 ALREADY_RESERVED", 9L);
    }

    // ---------------------------------------------------------------- yardimcilar

    private record Result(int status, String code) {
        String label() {
            return code == null ? String.valueOf(status) : status + " " + code;
        }
    }

    /** Tum gorevleri hazirlar, ayni anda baslatir ve sonuclari toplar. */
    private List<Result> sendSimultaneously(List<Function<Void, Result>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (Function<Void, Result> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.apply(null);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown(); // hepsi ayni anda
            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        }
        finally {
            pool.shutdownNow();
        }
    }

    private Result reserve(long eventId, long userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/events/" + eventId + "/reservations"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"userId\": " + userId + "}"))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), extractCode(response.body()));
        }
        catch (Exception ex) {
            return new Result(-1, ex.getClass().getSimpleName());
        }
    }

    private static String extractCode(String body) {
        int i = body.indexOf("\"code\":\"");
        if (i < 0) {
            return null;
        }
        int start = i + 8;
        return body.substring(start, body.indexOf('"', start));
    }

    private static Map<String, Long> summarize(List<Result> results) {
        return results.stream().collect(Collectors.groupingBy(Result::label, java.util.TreeMap::new, Collectors.counting()));
    }

    private long createEvent(int capacity) {
        jdbc.update("INSERT INTO event (name, starts_at, capacity) VALUES (?, '2027-06-01 18:00:00', ?)",
                "Eszamanlilik testi " + UUID.randomUUID(), capacity);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM event", Long.class);
        createdEventIds.add(id);
        return id;
    }

    private long createUser() {
        String email = "concurrency-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO app_user (email, full_name) VALUES (?, 'Eszamanlilik Testi')", email);
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
        createdUserIds.add(id);
        return id;
    }

    private long confirmedCount(long eventId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE event_id = ? AND status = 'CONFIRMED'", Long.class, eventId);
    }

    /** Yalnizca bu testin olusturdugu kayitlari siler; guvenlik icin test veritabanini dogrular. */
    @AfterEach
    void cleanUp() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        for (Long eventId : createdEventIds) {
            jdbc.update("DELETE FROM reservation WHERE event_id = ?", eventId);
            jdbc.update("DELETE FROM event WHERE id = ?", eventId);
        }
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM reservation WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
        createdEventIds.clear();
        createdUserIds.clear();
    }
}
