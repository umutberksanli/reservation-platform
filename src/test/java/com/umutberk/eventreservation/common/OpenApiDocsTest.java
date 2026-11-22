package com.umutberk.eventreservation.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

/**
 * OpenAPI dokumani tokensiz aciliyor mu, beklenen uclari ve Bearer semasini iceriyor mu?
 * Ayrica: dokumanda "herkese acik" gorunen her uc, gercekten de tokensiz 401 VERMEZ
 * (dokuman ile guvenlik kurallari birbirinden kopmasin).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsArePublicAndDescribeTheApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Reservation Platform API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/token'].post").exists())
                .andExpect(jsonPath("$.paths['/api/events/{eventId}/reservations'].post").exists())
                .andExpect(jsonPath("$.paths['/api/reservations/{id}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/reservations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users/{userId}/reservations'].get.parameters[?(@.name == 'size')]").exists())
                // herkese acik uclarda guvenlik gereksinimi bos, korumalilarda yok (global gecerli)
                .andExpect(jsonPath("$.paths['/api/auth/token'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/events'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/reservations/{id}/cancel'].post.security").doesNotExist());
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("swagger-ui"));
    }

    @Test
    void everyOperationMarkedPublicIsReallyReachableWithoutToken() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        Map<String, Map<String, Object>> paths = JsonPath.read(docs, "$.paths");

        int publicCount = 0;
        for (var path : paths.entrySet()) {
            for (var op : path.getValue().entrySet()) {
                @SuppressWarnings("unchecked")
                List<Object> security = (List<Object>) ((Map<String, Object>) op.getValue()).get("security");
                if (security == null || !security.isEmpty()) {
                    continue; // korumali
                }
                publicCount++;
                String url = path.getKey().replaceAll("\\{[^}]+}", "1");
                int status = mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(op.getKey().toUpperCase()), url)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn().getResponse().getStatus();
                assertThat(status).as("%s %s dokumanda herkese acik ama 401 dondu", op.getKey(), path.getKey())
                        .isNotEqualTo(401);
            }
        }
        assertThat(publicCount).isEqualTo(OpenApiConfig.PUBLIC_OPERATIONS.size());
    }
}
