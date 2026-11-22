package com.umutberk.eventreservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Her senaryonun sonunda veritabaninda dogrulanan kurallar.
 * Uygulama koduna degil, dogrudan tablolara bakar.
 */
final class WaitlistInvariants {

    private WaitlistInvariants() {
    }

    static void assertHold(JdbcTemplate jdbc, long eventId) {
        int capacity = jdbc.queryForObject("SELECT capacity FROM event WHERE id = ?", Integer.class, eventId);
        long confirmed = count(jdbc, eventId, "CONFIRMED");
        long waiting = count(jdbc, eventId, "WAITLISTED");

        // 1) Kapasite asilmaz.
        assertThat(confirmed).as("event %d: onayli rezervasyon kapasiteyi (%d) asmamali", eventId, capacity)
                .isLessThanOrEqualTo(capacity);

        // 2) Sirada bekleyen varken bos yer kalmaz (bos yer siradakine verilmis olmali).
        if (waiting > 0) {
            assertThat(confirmed).as("event %d: bekleyen varken bos yer kalmamali", eventId).isEqualTo(capacity);
        }

        // 3) Hicbir bekleyen atlanmaz: terfi edenlerin numarasi, hala bekleyenlerin hepsinden kucuk.
        Integer maxPromoted = jdbc.queryForObject(
                "SELECT MAX(waitlist_number) FROM reservation WHERE event_id = ? AND status = 'CONFIRMED'",
                Integer.class, eventId);
        Integer minWaiting = jdbc.queryForObject(
                "SELECT MIN(waitlist_number) FROM reservation WHERE event_id = ? AND status = 'WAITLISTED'",
                Integer.class, eventId);
        if (maxPromoted != null && minWaiting != null) {
            assertThat(maxPromoted).as("event %d: siradaki atlanmamali (terfi eden %d, bekleyen %d)",
                    eventId, maxPromoted, minWaiting).isLessThan(minWaiting);
        }

        // 4) Bekleyenlerin numaralari tekil ve kesin sirali (UNIQUE zaten garanti eder; burada okunur).
        List<Integer> numbers = waitingNumbers(jdbc, eventId);
        assertThat(numbers).doesNotHaveDuplicates().isSorted();
    }

    static long count(JdbcTemplate jdbc, long eventId, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reservation WHERE event_id = ? AND status = ?",
                Long.class, eventId, status);
    }

    static List<Integer> waitingNumbers(JdbcTemplate jdbc, long eventId) {
        return jdbc.queryForList(
                "SELECT waitlist_number FROM reservation WHERE event_id = ? AND status = 'WAITLISTED' ORDER BY waitlist_number",
                Integer.class, eventId);
    }

    /** Onayli kullanici ID'leri. */
    static List<Long> confirmedUsers(JdbcTemplate jdbc, long eventId) {
        return jdbc.queryForList(
                "SELECT user_id FROM reservation WHERE event_id = ? AND status = 'CONFIRMED' ORDER BY user_id",
                Long.class, eventId);
    }

    /** Bekleyen kullanici ID'leri, sira numarasina gore. */
    static List<Long> waitingUsersInOrder(JdbcTemplate jdbc, long eventId) {
        return jdbc.queryForList(
                "SELECT user_id FROM reservation WHERE event_id = ? AND status = 'WAITLISTED' ORDER BY waitlist_number",
                Long.class, eventId);
    }
}
