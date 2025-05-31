package cl.baezdaniel.telexample.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "telemetry.security.enabled=true",
    "telemetry.security.api-keys=valid-test-key,test-key-123,auth-test-key"
})
class TelemetryEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_TELEMETRY_JSON = """
        {
            "deviceId": "test",
            "latitude": 40.7128,
            "longitude": -74.0060,
            "timestamp": "2023-12-01T10:00:00"
        }
        """;

    @Test
    void shouldProtectTelemetrySubmissionEndpoint() throws Exception {
        // Test /api/telemetry requires authentication
        mockMvc.perform(post("/api/telemetry")
                .contentType("application/json")
                .content(VALID_TELEMETRY_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldAllowTelemetrySubmissionWithValidApiKey() throws Exception {
        // Test valid API key allows access
        mockMvc.perform(post("/api/telemetry")
                .header("Authorization", "Bearer valid-test-key")
                .contentType("application/json")
                .content(VALID_TELEMETRY_JSON))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldProtectTelemetryQueryEndpoint() throws Exception {
        // Test /api/telemetry/query requires authentication
        mockMvc.perform(get("/api/telemetry/query")
                .param("deviceId", "test"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldAllowTelemetryQueryWithValidApiKey() throws Exception {
        // Test valid API key allows query access
        mockMvc.perform(get("/api/telemetry/query")
                .header("Authorization", "Bearer valid-test-key")
                .param("deviceId", "test"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowHealthCheckWithoutAuth() throws Exception {
        // Test health endpoints remain accessible
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidApiKey() throws Exception {
        // Test invalid API key is rejected
        mockMvc.perform(post("/api/telemetry")
                .header("Authorization", "Bearer invalid-key")
                .contentType("application/json")
                .content(VALID_TELEMETRY_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldRejectMalformedAuthorizationHeader() throws Exception {
        // Test malformed header is rejected
        mockMvc.perform(post("/api/telemetry")
                .header("Authorization", "InvalidFormat")
                .contentType("application/json")
                .content(VALID_TELEMETRY_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldAllowBackwardCompatibilityEndpoint() throws Exception {
        // Test that the original /telemetry endpoint remains unprotected
        mockMvc.perform(post("/telemetry")
                .contentType("application/json")
                .content(VALID_TELEMETRY_JSON))
                .andExpect(status().isAccepted());
    }
} 