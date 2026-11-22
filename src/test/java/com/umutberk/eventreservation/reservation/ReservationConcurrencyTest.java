package com.umutberk.eventreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

import com.umutberk.eventreservation.security.JwtTokenService;
import com.umutberk.eventreservation.user.UserRole;

/**
 * Projenin kritik sarti: ayni anda gelen istekler kapasiteyi ASMAMALI ve siradaki kimse ATLANMAMALI.
 *
 * Gercek bir HTTP sunucusu (rastgele port) ve gercek MySQL test veritabani kullanilir.
 * Istekler bir "basla" sinyaliyle ayni anda gonderilir; her istek kendi thread'inde, kendi
 * transaction'inda calisir. Bu yuzden test @Transactional DEGILDIR; olusturdugu kayitlari
 * sonunda kendisi siler (yalnizca test veritabaninda, yalnizca kendi kayitlarini).
 * Her senaryonun sonunda WaitlistInvariants ile veritabani kurallari dogrulanir.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class ReservationConcurrencyTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    /** Istekler gercek, imzali JWT'lerle gider; sunucu bunlari gercek dogrulayicidan gecirir. */
    @Autowired
    private JwtTokenService tokens;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final List<Long> createdEventIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    /** A) 20 kullanici, kapasitesi 1 olan etkinlige ayni anda: tam 1 onay, 19 bekleyen (sira 1..19, bosluksuz). */
    @RepeatedTest(5)
    void simultaneousRequestsConfirmExactlyCapacityAndQueueTheRest() throws Exception {
        long eventId = createEvent(1);
        List<Long> users = createUsers(20);

        List<Result> results = sendSimultaneously(users.stream()
                .map(userId -> (Callable<Result>) () -> reserve(eventId, userId))
                .toList());

        log("A kapasite=1, 20 istek", results, eventId);
        assertThat(summarize(results)).containsExactlyInAnyOrderEntriesOf(Map.of("201", 1L, "202", 19L));
        assertThat(WaitlistInvariants.count(jdbc, eventId, "CONFIRMED")).isEqualTo(1);
        assertThat(WaitlistInvariants.waitingNumbers(jdbc, eventId))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 19).boxed().toList());
        WaitlistInvariants.assertHold(jdbc, eventId);
    }

    /** B1) Ayni kullanici bos etkinlige ayni anda 10 istek: tam 1 onay, 9 x 409. */
    @Test
    void simultaneousDuplicateRequestsCreateOneConfirmedReservation() throws Exception {
        long eventId = createEvent(5);
        long userId = createUsers(1).get(0);

        List<Result> results = sendSimultaneously(Collections.nCopies(10, () -> reserve(eventId, userId)));

        log("B1 ayni kullanici, bos etkinlik", results, eventId);
        assertThat(summarize(results)).containsExactlyInAnyOrderEntriesOf(Map.of("201", 1L, "409 ALREADY_RESERVED", 9L));
        assertThat(WaitlistInvariants.count(jdbc, eventId, "CONFIRMED")).isEqualTo(1);
        WaitlistInvariants.assertHold(jdbc, eventId);
    }

    /** B2) Ayni kullanici DOLU etkinlige ayni anda 10 istek: siraya tam 1 kez girer. */
    @Test
    void simultaneousDuplicateRequestsEnterWaitlistOnlyOnce() throws Exception {
        long eventId = createEvent(1);
        reserve(eventId, createUsers(1).get(0));
        long userId = createUsers(1).get(0);

        List<Result> results = sendSimultaneously(Collections.nCopies(10, () -> reserve(eventId, userId)));

        log("B2 ayni kullanici, dolu etkinlik", results, eventId);
        assertThat(summarize(results)).containsExactlyInAnyOrderEntriesOf(Map.of("202", 1L, "409 ALREADY_RESERVED", 9L));
        assertThat(WaitlistInvariants.waitingNumbers(jdbc, eventId)).containsExactly(1);
        WaitlistInvariants.assertHold(jdbc, eventId);
    }

    /**
     * C) Kapasite 3, 3 onayli + 5 bekleyen (sira 1..5). Ayni anda: 3 onayli iptal + 5 yeni kullanici.
     * Beklenen: terfi edenler TAM OLARAK siradaki ilk 3 kisi; yeni gelenlerin hicbiri onaylanmaz
     * (bos yer siradakine gider), hepsi 202 ile sonuna eklenir.
     */
    @RepeatedTest(3)
    void simultaneousCancellationsAndNewRequestsPromoteInFifoOrder() throws Exception {
        long eventId = createEvent(3);
        List<Long> confirmedUsers = createUsers(3);
        List<Long> waiters = createUsers(5);
        List<Long> newcomers = createUsers(5);
        List<Long> confirmedReservationIds = new ArrayList<>();
        for (long u : confirmedUsers) {
            Result r = reserve(eventId, u);
            assertThat(r.status()).isEqualTo(201);
            confirmedReservationIds.add(r.id());
        }
        for (long u : waiters) {
            assertThat(reserve(eventId, u).status()).isEqualTo(202);
        }

        List<Callable<Result>> tasks = new ArrayList<>();
        for (int i = 0; i < confirmedReservationIds.size(); i++) {
            long id = confirmedReservationIds.get(i);
            long owner = confirmedUsers.get(i);
            tasks.add(() -> cancel(id, owner));
        }
        newcomers.forEach(u -> tasks.add(() -> reserve(eventId, u)));
        Collections.shuffle(tasks);
        List<Result> results = sendSimultaneously(tasks);

        log("C 3 iptal + 5 yeni istek", results, eventId);
        assertThat(summarize(results)).containsExactlyInAnyOrderEntriesOf(Map.of("200", 3L, "202", 5L));
        assertThat(WaitlistInvariants.confirmedUsers(jdbc, eventId))
                .as("terfi edenler siradaki ilk 3 kisi olmali")
                .containsExactlyInAnyOrderElementsOf(waiters.subList(0, 3));
        List<Long> stillWaiting = WaitlistInvariants.waitingUsersInOrder(jdbc, eventId);
        assertThat(stillWaiting.subList(0, 2)).as("eski bekleyenler yeni gelenlerin onunde kalmali")
                .containsExactlyElementsOf(waiters.subList(3, 5));
        assertThat(stillWaiting.subList(2, 7)).containsExactlyInAnyOrderElementsOf(newcomers);
        WaitlistInvariants.assertHold(jdbc, eventId);
    }

    /** D) Ayni rezervasyon ayni anda 10 kez iptal: hepsi 200, yalnizca 1'i gercek iptal, TEK terfi. */
    @Test
    void simultaneousRepeatedCancelPromotesOnlyOnce() throws Exception {
        long eventId = createEvent(1);
        long confirmedOwner = createUsers(1).get(0);
        long confirmedId = reserve(eventId, confirmedOwner).id();
        List<Long> waiters = createUsers(3);
        for (long u : waiters) {
            reserve(eventId, u);
        }

        List<Result> results = sendSimultaneously(Collections.nCopies(10, () -> cancel(confirmedId, confirmedOwner)));

        log("D ayni iptal x10", results, eventId);
        assertThat(summarize(results)).containsExactlyInAnyOrderEntriesOf(Map.of("200", 10L));
        assertThat(results.stream().filter(r -> Boolean.FALSE.equals(r.alreadyCancelled())).count()).isEqualTo(1);
        assertThat(WaitlistInvariants.confirmedUsers(jdbc, eventId)).containsExactly(waiters.get(0));
        assertThat(WaitlistInvariants.waitingUsersInOrder(jdbc, eventId)).containsExactlyElementsOf(waiters.subList(1, 3));
        WaitlistInvariants.assertHold(jdbc, eventId);
    }

    // ---------------------------------------------------------------- yardimcilar

    private record Result(int status, String code, Long id, Boolean alreadyCancelled) {
        String label() {
            return code == null ? String.valueOf(status) : status + " " + code;
        }
    }

    /** Tum gorevleri hazirlar, ayni anda baslatir ve sonuclari toplar. */
    private static List<Result> sendSimultaneously(List<Callable<Result>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (Callable<Result> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
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

    /** Kullanici kendi token'iyla, kendi adina rezervasyon yapar. */
    private Result reserve(long eventId, long userId) {
        return send("/api/events/" + eventId + "/reservations", "", userId);
    }

    /** Kaydin sahibi kendi token'iyla iptal eder. */
    private Result cancel(long reservationId, long ownerUserId) {
        return send("/api/reservations/" + reservationId + "/cancel", "", ownerUserId);
    }

    private Result send(String path, String body, long userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + tokens.issue(userId, UserRole.USER).accessToken())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();
            String already = find(json, "\"alreadyCancelled\":(true|false)");
            String id = find(json, "^\\{\"id\":(\\d+)");
            return new Result(response.statusCode(), find(json, "\"code\":\"([A-Z_]+)\""),
                    id == null ? null : Long.valueOf(id), already == null ? null : Boolean.valueOf(already));
        }
        catch (Exception ex) {
            return new Result(-1, ex.getClass().getSimpleName(), null, null);
        }
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static Map<String, Long> summarize(List<Result> results) {
        return results.stream().collect(Collectors.groupingBy(Result::label, TreeMap::new, Collectors.counting()));
    }

    private void log(String scenario, List<Result> results, long eventId) {
        System.out.printf("[%s] cevaplar: %s | onayli=%d bekleyen=%d sira=%s%n", scenario, summarize(results),
                WaitlistInvariants.count(jdbc, eventId, "CONFIRMED"), WaitlistInvariants.count(jdbc, eventId, "WAITLISTED"),
                WaitlistInvariants.waitingNumbers(jdbc, eventId));
    }

    private long createEvent(int capacity) {
        jdbc.update("INSERT INTO event (name, starts_at, capacity) VALUES (?, '2027-06-01 18:00:00', ?)",
                "Eszamanlilik testi " + UUID.randomUUID(), capacity);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM event", Long.class);
        createdEventIds.add(id);
        return id;
    }

    private List<Long> createUsers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String email = "concurrency-" + UUID.randomUUID() + "@example.com";
            jdbc.update("INSERT INTO app_user (email, full_name) VALUES (?, 'Eszamanlilik Testi')", email);
            long id = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
            createdUserIds.add(id);
            ids.add(id);
        }
        return ids;
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
