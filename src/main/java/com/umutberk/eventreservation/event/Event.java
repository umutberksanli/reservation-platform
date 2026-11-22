package com.umutberk.eventreservation.event;

import java.time.Instant;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * "event" tablosunun Java karsiligi. Tablo Flyway V1 ile olusturulur;
 * Hibernate burada yalnizca okuma/yazma yapar, tabloyu degistirmez.
 */
@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int capacity;

    /** Degeri veritabani verir (DEFAULT CURRENT_TIMESTAMP); kayittan sonra Hibernate okur. */
    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** JPA'nin ihtiyac duydugu bos constructor; uygulama kodu kullanmaz. */
    protected Event() {
    }

    public Event(String name, Instant startsAt, int capacity) {
        this.name = name;
        this.startsAt = startsAt;
        this.capacity = capacity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
