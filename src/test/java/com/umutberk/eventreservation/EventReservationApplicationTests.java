package com.umutberk.eventreservation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tum uygulamayi (veritabani baglantisi dahil) ayaga kaldirir.
 * Gercek MySQL gerektirir (DB_TEST_URL ya da Testcontainers; bkz. TestDatabaseInitializer).
 */
@SpringBootTest
@ActiveProfiles("test")
class EventReservationApplicationTests {

	@Test
	void contextLoads() {
	}

}
