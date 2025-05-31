package cl.baezdaniel.telexample.integration;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for telemetry endpoint authentication
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class TelemetryAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
            stmt.execute("DELETE FROM alerts");
        }
    }

    private void createTestTelemetryData(String deviceId, double latitude, double longitude) {
        Telemetry telemetry = new Telemetry();
        telemetry.setDeviceId(deviceId);
        telemetry.setLatitude(latitude);
        telemetry.setLongitude(longitude);
        telemetry.setTimestamp(LocalDateTime.now().minusMinutes(5)); // 5 minutes ago
        telemetryRepository.save(telemetry);
    }

    @Test
    void shouldAllowTelemetrySubmissionWithValidApiKey() throws Exception {
        // Test telemetry submission with valid API key
        Telemetry telemetry = new Telemetry();
        telemetry.setDeviceId("test-device-001");
        telemetry.setLatitude(40.7128);
        telemetry.setLongitude(-74.0060);
        telemetry.setTimestamp(LocalDateTime.now());

        mockMvc.perform(post("/telemetry")
                .header("Authorization", "Bearer telemetry-api-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId").value("test-device-001"))
                .andExpect(jsonPath("$.message").value("Telemetry data saved successfully"));
    }

    @Test
    void shouldRejectTelemetrySubmissionWithInvalidApiKey() throws Exception {
        // Test telemetry submission with invalid API key
        Telemetry telemetry = new Telemetry();
        telemetry.setDeviceId("test-device-002");
        telemetry.setLatitude(40.7128);
        telemetry.setLongitude(-74.0060);
        telemetry.setTimestamp(LocalDateTime.now());

        mockMvc.perform(post("/telemetry")
                .header("Authorization", "Bearer invalid-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetry)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Valid API key required"));
    }

    @Test
    void shouldRejectTelemetrySubmissionWithoutApiKey() throws Exception {
        // Test telemetry submission without API key
        Telemetry telemetry = new Telemetry();
        telemetry.setDeviceId("test-device-003");
        telemetry.setLatitude(40.7128);
        telemetry.setLongitude(-74.0060);
        telemetry.setTimestamp(LocalDateTime.now());

        mockMvc.perform(post("/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetry)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Valid API key required"));
    }

    @Test
    void shouldAllowTelemetryQueryWithValidApiKey() throws Exception {
        // Setup: Create test data first
        createTestTelemetryData("test-device-001", 40.7128, -74.0060);

        // Test telemetry query with valid API key
        mockMvc.perform(get("/devices/test-device-001/telemetry/latest")
                .header("Authorization", "Bearer production-key-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("test-device-001"))
                .andExpect(jsonPath("$.latitude").value(40.7128))
                .andExpect(jsonPath("$.longitude").value(-74.0060));
    }

    @Test
    void shouldRejectTelemetryQueryWithInvalidApiKey() throws Exception {
        // Setup: Create test data first
        createTestTelemetryData("test-device-001", 40.7128, -74.0060);

        // Test telemetry query with invalid API key
        mockMvc.perform(get("/devices/test-device-001/telemetry/latest")
                .header("Authorization", "Bearer invalid-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Valid API key required"));
    }

    @Test
    void shouldReturnNotFoundWhenNoDataExistsWithValidApiKey() throws Exception {
        // Test telemetry query for non-existent device with valid API key
        mockMvc.perform(get("/devices/non-existent-device/telemetry/latest")
                .header("Authorization", "Bearer production-key-456"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowHealthCheckWithoutAuthentication() throws Exception {
        // Test that health check endpoint remains accessible without authentication
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * Test with authentication disabled for backward compatibility
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "endpoint.auth.enabled=false"
    })
    @Transactional
    static class AuthenticationDisabledIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private TelemetryRepository telemetryRepository;

        @Autowired
        private DataSource dataSource;

        @BeforeEach
        void setUp() throws Exception {
            // Reset database state between tests
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM telemetry");
                stmt.execute("DELETE FROM alerts");
            }
        }

        private void createTestTelemetryData(String deviceId, double latitude, double longitude) {
            Telemetry telemetry = new Telemetry();
            telemetry.setDeviceId(deviceId);
            telemetry.setLatitude(latitude);
            telemetry.setLongitude(longitude);
            telemetry.setTimestamp(LocalDateTime.now().minusMinutes(5)); // 5 minutes ago
            telemetryRepository.save(telemetry);
        }

        @Test
        void shouldAllowTelemetrySubmissionWithoutApiKeyWhenAuthDisabled() throws Exception {
            // Test telemetry submission without API key when auth is disabled
            Telemetry telemetry = new Telemetry();
            telemetry.setDeviceId("test-device-no-auth");
            telemetry.setLatitude(40.7128);
            telemetry.setLongitude(-74.0060);
            telemetry.setTimestamp(LocalDateTime.now());

            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetry)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.deviceId").value("test-device-no-auth"))
                    .andExpect(jsonPath("$.message").value("Telemetry data saved successfully"));
        }

        @Test
        void shouldAllowTelemetryQueryWithoutApiKeyWhenAuthDisabled() throws Exception {
            // Setup: Create test data first
            createTestTelemetryData("test-device-no-auth", 40.7128, -74.0060);

            // Test telemetry query without API key when auth is disabled
            mockMvc.perform(get("/devices/test-device-no-auth/telemetry/latest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deviceId").value("test-device-no-auth"))
                    .andExpect(jsonPath("$.latitude").value(40.7128))
                    .andExpect(jsonPath("$.longitude").value(-74.0060));
        }

        @Test
        void shouldAllowTelemetrySubmissionWithApiKeyWhenAuthDisabled() throws Exception {
            // Test that even with API key, it still works when auth is disabled
            Telemetry telemetry = new Telemetry();
            telemetry.setDeviceId("test-device-with-key");
            telemetry.setLatitude(40.7128);
            telemetry.setLongitude(-74.0060);
            telemetry.setTimestamp(LocalDateTime.now());

            mockMvc.perform(post("/telemetry")
                    .header("Authorization", "Bearer any-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetry)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.deviceId").value("test-device-with-key"))
                    .andExpect(jsonPath("$.message").value("Telemetry data saved successfully"));
        }
    }
} 