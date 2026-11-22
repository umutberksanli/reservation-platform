package com.umutberk.eventreservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tum uygulamayi (veritabani baglantisi dahil) ayaga kaldirir.
 * Gercek MySQL gerektirdigi icin yalnizca DB_PASSWORD tanimliysa calisir.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class EventReservationApplicationTests {

	@Test
	void contextLoads() {
	}

}
