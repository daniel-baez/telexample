package cl.baezdaniel.telexample.controllers;

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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TelemetryController
 * Tests the REST API endpoints for telemetry data submission and retrieval
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "endpoint.auth.enabled=false")
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
        }
    }

    /**
     * Tests that posting telemetry data without a deviceId returns a 400 Bad Request.
     * This verifies that the API enforces the required deviceId field.
     */
    @Test
    void testPostTelemetryWithMissingDeviceId() throws Exception {
        Map<String, Object> telemetryData = new HashMap<>();
        telemetryData.put("latitude", 40.7128);
        telemetryData.put("longitude", -74.0060);
        telemetryData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Tests that posting valid telemetry data with all required fields returns a 202 Accepted
     * and includes an ID in the response. This verifies the successful creation of telemetry records.
     */
    @Test
    void testPostTelemetryWithValidData() throws Exception {
        Map<String, Object> telemetryData = new HashMap<>();
        telemetryData.put("deviceId", "device123");
        telemetryData.put("latitude", 40.7128);
        telemetryData.put("longitude", -74.0060);
        telemetryData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNumber());
    }

    /**
     * Tests that requesting the latest telemetry for a non-existent device returns a 404 Not Found.
     * This verifies proper error handling for unknown devices.
     */
    @Test
    void testGetLatestTelemetryWhenNoData() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry/devices/nonexistent/latest"))
                .andExpect(status().isNotFound());
    }

    /**
     * Tests the retrieval of the latest telemetry data for a device after posting multiple records.
     * Verifies that:
     * 1. Multiple telemetry records can be posted for the same device
     * 2. The latest endpoint returns the most recent record based on timestamp
     * 3. The returned data matches the last posted record's values
     */
    @Test
    void testGetLatestTelemetryAfterTwoPosts() throws Exception {
        LocalDateTime t1 = LocalDateTime.now().minusHours(1);
        LocalDateTime t2 = LocalDateTime.now();

        // First post with earlier timestamp
        Map<String, Object> telemetryData1 = new HashMap<>();
        telemetryData1.put("deviceId", "device456");
        telemetryData1.put("latitude", 40.7128);
        telemetryData1.put("longitude", -74.0060);
        telemetryData1.put("timestamp", t1.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData1)))
                .andExpect(status().isAccepted());

        // Second post with later timestamp
        Map<String, Object> telemetryData2 = new HashMap<>();
        telemetryData2.put("deviceId", "device456");
        telemetryData2.put("latitude", 41.8781);
        telemetryData2.put("longitude", -87.6298);
        telemetryData2.put("timestamp", t2.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData2)))
                .andExpect(status().isAccepted());

        // Get latest should return the second record (with T2)
        mockMvc.perform(get("/api/v1/telemetry/devices/device456/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("device456"))
                .andExpect(jsonPath("$.latitude").value(41.8781))
                .andExpect(jsonPath("$.longitude").value(-87.6298));
    }
} 