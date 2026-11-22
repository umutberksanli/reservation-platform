package com.umutberk.eventreservation.security;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.umutberk.eventreservation.security.dto.TokenResponse;
import com.umutberk.eventreservation.user.AppUser;
import com.umutberk.eventreservation.user.UserRole;

/** Kullanici icin imzali, kisa omurlu erisim token'i (JWT, HS256) uretir. */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    /** sub = kullanici ID'si, roles = [USER|ADMIN]. E-posta/ad gibi kisisel bilgi token'a konmaz. */
    public TokenResponse issue(AppUser user) {
        return issue(user.getId(), user.getRole());
    }

    public TokenResponse issue(long userId, UserRole role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim("roles", List.of(role.name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttl().toSeconds());
    }
}
