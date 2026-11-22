package com.umutberk.eventreservation.common;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI dokumani: /v3/api-docs (JSON) ve /swagger-ui.html (tarayicida dene).
 * Varsayilan olarak her uc Bearer JWT ister; herkese acik uclar asagidaki listede isaretlenir
 * (WebSecurityConfig'teki permitAll kurallariyla ayni; OpenApiDocsTest ikisinin uyumunu sinar).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    /** "METOT /yol" bicimi, OpenAPI yol sablonlariyla. */
    static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "GET /api/health",
            "GET /api/events",
            "GET /api/events/{id}",
            "POST /api/users",
            "POST /api/auth/token");

    @Bean
    OpenAPI reservationPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reservation Platform API")
                        .version("v1")
                        .description("""
                                Kontenjanli etkinlik rezervasyonu ve FIFO bekleme listesi.
                                Giris: POST /api/auth/token ile JWT alin, "Authorize" dugmesine yapistirin.
                                Hatalar application/problem+json doner ve her cevapta sabit bir "code" alani vardir."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Herkese acik uclarda guvenlik gereksinimini kaldirir (dokumanda kilit simgesi gorunmez). */
    @Bean
    OpenApiCustomizer publicOperationsCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (isPublic(method, path)) {
                    operation.setSecurity(List.of());
                }
            }));
        };
    }

    static boolean isPublic(PathItem.HttpMethod method, String path) {
        return PUBLIC_OPERATIONS.contains(method.name() + " " + path);
    }
}
