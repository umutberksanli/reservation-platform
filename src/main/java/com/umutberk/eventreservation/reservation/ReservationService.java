package com.umutberk.eventreservation.reservation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.umutberk.eventreservation.event.Event;
import com.umutberk.eventreservation.event.EventNotFoundException;
import com.umutberk.eventreservation.event.EventRepository;
import com.umutberk.eventreservation.reservation.ReservationProblems.AlreadyReservedException;
import com.umutberk.eventreservation.reservation.ReservationProblems.EventFullException;
import com.umutberk.eventreservation.reservation.ReservationProblems.ReservationNotFoundException;
import com.umutberk.eventreservation.reservation.dto.ReservationResponse;
import com.umutberk.eventreservation.user.AppUser;
import com.umutberk.eventreservation.user.AppUserRepository;
import com.umutberk.eventreservation.user.UserNotFoundException;

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
     * Kapasiteyi asmadan rezervasyon olusturur. Ayni anda gelen istekler icin iki kural:
     *
     * 1. ILK IS etkinlik satiri kilitlenir (SELECT ... FOR UPDATE). Ayni etkinlige gelen diger
     *    istekler kilit birakilana (commit/rollback) kadar bekler; "say -> kontrol et -> yaz"
     *    adimlari bir etkinlik icin artik sirayla calisir.
     * 2. READ COMMITTED: kilit alindiktan sonraki sayim, onceki isteklerin commit ettigi
     *    rezervasyonlari gorur. MySQL'in varsayilani REPEATABLE READ'de sayim, transaction'in
     *    ilk okumasindaki eski "fotografa" bakabilir ve kapasite yine asilabilir.
     *
     * Ayni kullanicinin mukerrer istekleri ayrica V2'deki uk_reservation_active_user_event
     * kisitiyla da engellenir.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse create(long eventId, long userId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (reservationRepository.existsByEventIdAndUserIdAndStatus(eventId, userId, ReservationStatus.CONFIRMED)) {
            throw new AlreadyReservedException(eventId, userId, null);
        }
        long confirmed = reservationRepository.countByEventIdAndStatus(eventId, ReservationStatus.CONFIRMED);
        if (confirmed >= event.getCapacity()) {
            throw new EventFullException(eventId);
        }

        try {
            return ReservationResponse.from(reservationRepository.saveAndFlush(new Reservation(event, user)));
        }
        catch (DataIntegrityViolationException ex) {
            if (isActiveReservationViolation(ex)) {
                throw new AlreadyReservedException(eventId, userId, ex);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(long id) {
        return reservationRepository.findById(id)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    private static boolean isActiveReservationViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(ACTIVE_RESERVATION_UNIQUE_CONSTRAINT);
    }
}
