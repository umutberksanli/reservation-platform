package com.umutberk.eventreservation.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP guvenlik kurallari (yalnizca web uygulamasi olarak calisirken; web'siz acilista, ornegin
 * yalnizca migration icin, yuklenmez). API durumsuzdur: her istek "Authorization: Bearer <JWT>" tasir.
 *
 * Herkese acik: saglik kontrolu, etkinlik listesi/detayi, kayit olma, giris, API dokumantasyonu.
 * ADMIN: etkinlik olusturma. Geri kalan her sey: giris yapmis kullanici (sahiplik servislerde kontrol edilir).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // cookie/session yok; token basliktan gelir
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/events", "/api/events/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/token").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(ProblemJsonSecurityHandlers.unauthorized())
                        .accessDeniedHandler(ProblemJsonSecurityHandlers.forbidden()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(ProblemJsonSecurityHandlers.unauthorized())
                        .accessDeniedHandler(ProblemJsonSecurityHandlers.forbidden()));
        return http.build();
    }

    /** JWT'deki "roles" alani -> ROLE_USER / ROLE_ADMIN; kimlik (getName) = "sub" = kullanici ID'si. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
