package com.otpservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End to end against a real Postgres: the migration applies, the schema validates, and two apps cannot see
 * each other's codes. Needs Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpServiceIntegrationTest {

    private static final String ADMIN = "admin-token-for-tests-".repeat(2);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("otp.code-pepper", () -> "pepper-for-tests-".repeat(3));
        registry.add("otp.admin-token", () -> ADMIN);
        registry.add("otp.sms.provider", () -> "log");
        registry.add("otp.sms.allow-log", () -> "true");
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void oneAppsRequestIdIsUnknownToAnother() {
        String shopKey = createApp("Shop").get("firstKey").get("apiKey").asText();
        String clinicKey = createApp("Clinic").get("firstKey").get("apiKey").asText();

        ResponseEntity<JsonNode> sent = rest.exchange("/v1/otp/send", HttpMethod.POST,
                withKey(shopKey, Map.of("phone", "+919876543210")), JsonNode.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sent.getBody().get("requestId").asText();
        assertThat(sent.getBody().has("code")).isFalse();

        ResponseEntity<JsonNode> crossed = rest.exchange("/v1/otp/verify", HttpMethod.POST,
                withKey(clinicKey, Map.of("requestId", requestId, "code", "123456")), JsonNode.class);
        assertThat(crossed.getBody().get("reason").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void noKeyAndNoAdminTokenAreRefused() {
        assertThat(rest.postForEntity("/v1/otp/send", Map.of("phone", "+919876543210"), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/admin/apps", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JsonNode createApp(String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ADMIN);
        return rest.exchange("/admin/apps", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), headers), JsonNode.class).getBody();
    }

    private static HttpEntity<Map<String, String>> withKey(String key, Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", key);
        return new HttpEntity<>(body, headers);
    }
}
