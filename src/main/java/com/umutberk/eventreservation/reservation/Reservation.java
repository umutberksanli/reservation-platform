package com.umutberk.eventreservation.reservation;

import java.time.Instant;

import org.hibernate.annotations.Generated;

import com.umutberk.eventreservation.event.Event;
import com.umutberk.eventreservation.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * "reservation" tablosu (Flyway V2). active_key sutunu MySQL tarafindan hesaplanir
 * ve yalnizca benzersizlik kurali icin vardir; bu yuzden burada eslenmez.
 */
@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Reservation() {
    }

    /** Yeni rezervasyon her zaman CONFIRMED baslar. */
    public Reservation(Event event, AppUser user) {
        this.event = event;
        this.user = user;
        this.status = ReservationStatus.CONFIRMED;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public AppUser getUser() {
        return user;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
