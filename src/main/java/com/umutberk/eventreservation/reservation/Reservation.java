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
 * "reservation" tablosu (Flyway V2 + V3). Onayli rezervasyon da, bekleme listesindeki kayit da
 * bu tablodadir; fark "status" alanindadir. active_key sutunu MySQL tarafindan hesaplanir ve
 * yalnizca benzersizlik kurali icin vardir; bu yuzden burada eslenmez.
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

    /** Etkinlik icindeki kalici sira numarasi. Dogrudan onaylananlarda null; terfiden sonra da korunur. */
    @Column(name = "waitlist_number")
    private Integer waitlistNumber;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Reservation() {
    }

    private Reservation(Event event, AppUser user, ReservationStatus status, Integer waitlistNumber) {
        this.event = event;
        this.user = user;
        this.status = status;
        this.waitlistNumber = waitlistNumber;
    }

    /** Bos yer var: dogrudan onayli. */
    public static Reservation confirmed(Event event, AppUser user) {
        return new Reservation(event, user, ReservationStatus.CONFIRMED, null);
    }

    /** Etkinlik dolu: verilen sira numarasiyla bekleme listesine. */
    public static Reservation waitlisted(Event event, AppUser user, int waitlistNumber) {
        return new Reservation(event, user, ReservationStatus.WAITLISTED, waitlistNumber);
    }

    /** Bekleme listesinden onayli rezervasyona gecis. */
    public void promote(Instant now) {
        if (status != ReservationStatus.WAITLISTED) {
            throw new IllegalStateException("Only a waitlisted reservation can be promoted, was " + status);
        }
        this.status = ReservationStatus.CONFIRMED;
        this.promotedAt = now;
    }

    /** Onayli ya da bekleyen kaydi iptal eder. Sira numarasi tarihce icin korunur. */
    public void cancel(Instant now) {
        if (status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Reservation " + id + " is already cancelled");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
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

    public Integer getWaitlistNumber() {
        return waitlistNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
