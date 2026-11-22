package com.umutberk.eventreservation.user;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.umutberk.eventreservation.user.dto.RegisterUserRequest;
import com.umutberk.eventreservation.user.dto.UserResponse;

@Service
public class AppUserService {

    /** V1 migration'daki UNIQUE kisitin adi. */
    static final String EMAIL_UNIQUE_CONSTRAINT = "uk_app_user_email";

    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * E-posta benzersizligi iki katmanda korunur:
     * 1. Java on kontrolu: normal durumda tekrar eden e-postayi hizlica yakalar.
     * 2. Veritabani UNIQUE kisiti: iki istek ayni anda gelip ikisi de 1. adimi gecse bile
     *    MySQL ikinci kaydi reddeder; bu hata da 409'a cevrilir.
     */
    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        String email = normalizeEmail(request.email());

        if (appUserRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(null);
        }
        try {
            AppUser saved = appUserRepository.saveAndFlush(new AppUser(email, request.fullName()));
            return UserResponse.from(saved);
        }
        catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw new EmailAlreadyRegisteredException(ex);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse findById(long id) {
        return appUserRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /** E-postalar tek bicimde (kucuk harf) saklanir. Bosluklar DTO'da zaten kirpildi. */
    static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT);
    }
}
