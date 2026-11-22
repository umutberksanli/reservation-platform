package com.umutberk.eventreservation.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Sutunun karsilastirma ayari (utf8mb4_0900_ai_ci) buyuk/kucuk harf duyarsiz oldugu icin
     * "Ayse@x.com" ile "ayse@x.com" ayni kabul edilir; sorgu indeksi de kullanabilir.
     */
    boolean existsByEmail(String email);

    Optional<AppUser> findByEmail(String email);
}
