package com.umutberk.eventreservation.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Uygulamanin ayakta oldugunu gosteren basit bir endpoint.
 * Tarayicida http://localhost:8080/api/health adresini ac.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "event-reservation");
    }
}
