package com.umutberk.eventreservation.reservation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.umutberk.eventreservation.common.PageResponse;
import com.umutberk.eventreservation.event.Event;
import com.umutberk.eventreservation.event.EventNotFoundException;
import com.umutberk.eventreservation.event.EventRepository;
import com.umutberk.eventreservation.reservation.ReservationProblems.AlreadyReservedException;
import com.umutberk.eventreservation.reservation.ReservationProblems.NoActiveReservationException;
import com.umutberk.eventreservation.reservation.ReservationProblems.ReservationNotFoundException;
import com.umutberk.eventreservation.reservation.dto.CancellationResponse;
import com.umutberk.eventreservation.reservation.dto.ReservationResponse;
import com.umutberk.eventreservation.reservation.dto.UserReservationResponse;
import com.umutberk.eventreservation.security.Actor;
import com.umutberk.eventreservation.user.AppUser;
import com.umutberk.eventreservation.user.AppUserRepository;
import com.umutberk.eventreservation.user.UserNotFoundException;

/**
 * Kontenjanli rezervasyon + FIFO bekleme listesi.
 *
 * Eszamanlilik kurali (tum yazma islemleri icin ayni):
 *   1. ILK IS etkinlik satiri kilitlenir (SELECT ... FOR UPDATE). Ayni etkinlige ait olusturma,
 *      iptal ve terfi islemleri boylece tek sirada calisir.
 *   2. Transaction READ COMMITTED calisir: kilitten sonraki her okuma, onceki islemlerin commit
 *      ettigi son durumu gorur (REPEATABLE READ'deki eski "fotograf" sorunu olmaz).
 *   3. Siradaki bekleyen her zaman en kucuk waitlist_number'dir; SKIP LOCKED kullanilmaz,
 *      yani kilitli bir satir "atlanip" arkadaki secilmez.
 */
@Service
public class ReservationService {

    static final String ACTIVE_RESERVATION_UNIQUE_CONSTRAINT = "uk_reservation_active_user_event";

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final AppUserRepository appUserRepository;

    public ReservationService(ReservationRepository reservationRepository,
            EventRepository eventRepository,
            AppUserRepository appUserRepository) {
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Yer varsa onayli rezervasyon, yoksa bekleme listesi kaydi olusturur.
     * Sirada bekleyen varken bos bir yer yeni gelene verilmez: once bekleyenler terfi eder.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse create(long eventId, long userId) {
        Event event = lockEvent(eventId);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        reservationRepository.findActive(eventId, userId, ReservationStatus.ACTIVE).ifPresent(existing -> {
            throw new AlreadyReservedException(eventId, userId, existing.getStatus(), null);
        });

        // FIFO guvencesi: bos yer varsa once siradakiler alir.
        fillFreeSeatsFromWaitlist(event);

        Reservation reservation;
        if (countConfirmed(eventId) < event.getCapacity()) {
            reservation = Reservation.confirmed(event, user);
        }
        else {
            int nextNumber = reservationRepository.findMaxWaitlistNumber(eventId) + 1;
            reservation = Reservation.waitlisted(event, user, nextNumber);
        }

        try {
            return toResponse(reservationRepository.saveAndFlush(reservation));
        }
        catch (DataIntegrityViolationException ex) {
            if (isActiveReservationViolation(ex)) {
                throw new AlreadyReservedException(eventId, userId, null, ex);
            }
            throw ex;
        }
    }

    /**
     * Onayli ya da bekleyen kaydi iptal eder. Onayli bir yer bosalirsa AYNI transaction'da
     * siradaki bekleyen onayliya gecer. Zaten iptal edilmis kayit icin hicbir sey degismez
     * (alreadyCancelled = true); ikinci bir terfi yapilmaz.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CancellationResponse cancel(long reservationId, Actor actor) {
        // Kilitlenecek etkinligi bul (event_id degismez); kaydin DURUMU kilitten sonra okunur.
        long eventId = reservationRepository.findEventIdById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Event event = lockEvent(eventId);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        // Yalnizca sahibi ya da ADMIN; aksi halde 403 ve hicbir sey degismez (sahip hic degismeyen bir alandir).
        actor.requireSelfOrAdmin(reservation.getUser().getId());

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return new CancellationResponse(toResponse(reservation), true, List.of());
        }

        reservation.cancel(now());
        reservationRepository.flush();

        List<ReservationResponse> promoted = fillFreeSeatsFromWaitlist(event).stream()
                .map(this::toResponse)
                .toList();
        return new CancellationResponse(toResponse(reservation), false, promoted);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public ReservationResponse findById(long id, Actor actor) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        actor.requireSelfOrAdmin(reservation.getUser().getId());
        return toResponse(reservation);
    }

    /** Kullanicinin bu etkinlikteki guncel durumu (onayli ya da bekleyen + sira konumu). */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public ReservationResponse findActive(long eventId, long userId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        if (!appUserRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return reservationRepository.findActive(eventId, userId, ReservationStatus.ACTIVE)
                .map(this::toResponse)
                .orElseThrow(() -> new NoActiveReservationException(eventId, userId));
    }

    /** Kullanicinin kayitlari, en yeni once. status null ise hepsi. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public PageResponse<UserReservationResponse> listForUser(long userId, ReservationStatus status, int page, int size) {
        if (!appUserRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(reservationRepository.findForUser(userId, status, pageRequest),
                r -> UserReservationResponse.from(r, waitlistPosition(r)));
    }

    // ------------------------------------------------------------------ yardimcilar

    private Event lockEvent(long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    /**
     * Kapasitede bos yer kaldikca siradaki (en kucuk numarali) bekleyeni onaylar.
     * Etkinlik kilitliyken cagrilmalidir. Normal akista en fazla bir kisi terfi eder.
     */
    private List<Reservation> fillFreeSeatsFromWaitlist(Event event) {
        List<Reservation> promoted = new ArrayList<>();
        long confirmed = countConfirmed(event.getId());
        while (confirmed < event.getCapacity()) {
            Reservation next = reservationRepository
                    .findFirstByEventIdAndStatusOrderByWaitlistNumberAsc(event.getId(), ReservationStatus.WAITLISTED)
                    .orElse(null);
            if (next == null) {
                break;
            }
            next.promote(now());
            reservationRepository.flush();
            promoted.add(next);
            confirmed++;
        }
        return promoted;
    }

    /** MySQL DATETIME(6) mikrosaniye saklar; cevaptaki zaman ile kayitli zaman ayni olsun. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private long countConfirmed(long eventId) {
        return reservationRepository.countByEventIdAndStatus(eventId, ReservationStatus.CONFIRMED);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return ReservationResponse.from(reservation, waitlistPosition(reservation));
    }

    /** Bekleyen icin sira konumu (1 = siradaki); digerleri icin null. */
    private Integer waitlistPosition(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.WAITLISTED) {
            return null;
        }
        return (int) reservationRepository.countAhead(reservation.getEvent().getId(),
                ReservationStatus.WAITLISTED, reservation.getWaitlistNumber()) + 1;
    }

    private static boolean isActiveReservationViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(ACTIVE_RESERVATION_UNIQUE_CONSTRAINT);
    }
}
