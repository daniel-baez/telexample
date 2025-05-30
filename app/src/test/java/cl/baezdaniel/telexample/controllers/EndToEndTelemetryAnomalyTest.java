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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests that verify the complete telemetry processing pipeline
 * by sending invalid telemetry data via HTTP POST and verifying alert creation via HTTP GET.
 * 
 * These tests fill the gap identified in the test suite where no tests combine:
 * 1. HTTP POST to /api/v1/telemetry with invalid coordinates
 * 2. HTTP GET from /api/v1/alerts to verify ANOMALY alert creation
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "endpoint.auth.enabled=true")
class EndToEndTelemetryAnomalyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    // Test API key from application-test.properties
    private static final String TEST_API_KEY = "valid-test-key";

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
            stmt.execute("DELETE FROM alerts");
        }
    }

    /**
     * Helper method to create telemetry JSON payload
     */
    private Map<String, Object> createTelemetryPayload(String deviceId, double latitude, double longitude) {
        Map<String, Object> telemetryData = new HashMap<>();
        telemetryData.put("deviceId", deviceId);
        telemetryData.put("latitude", latitude);
        telemetryData.put("longitude", longitude);
        telemetryData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return telemetryData;
    }

    /**
     * Helper method to wait for async telemetry processing to complete
     */
    private void waitForAsyncProcessing() throws InterruptedException {
        Thread.sleep(100); // Give time for async event processing
    }

    /**
     * Test Case 1: Invalid Latitude (> 90) - End-to-End HTTP Flow
     * 
     * This test verifies the complete pipeline:
     * 1. POST invalid telemetry data (lat = 95.0) via HTTP endpoint with authentication
     * 2. Verify telemetry is queued (returns 202 Accepted)
     * 3. Wait for async anomaly detection processing
     * 4. GET alerts via HTTP endpoint with authentication to verify ANOMALY alert was created
     */
    @Test
    void testInvalidLatitudeCreatesAnomalyAlert_EndToEnd() throws Exception {
        String deviceId = "invalid-lat-device";
        double invalidLatitude = 95.0; // Invalid: latitude cannot be > 90
        double validLongitude = -74.0060;

        // Step 1: POST invalid telemetry data via HTTP with authentication
        Map<String, Object> telemetryData = createTelemetryPayload(deviceId, invalidLatitude, validLongitude);

        mockMvc.perform(post("/api/v1/telemetry")
                .header("Authorization", "Bearer " + TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted());

        // Step 2: Wait for async anomaly detection processing
        waitForAsyncProcessing();

        // Step 3: GET alerts via HTTP with authentication to verify ANOMALY alert was created
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].deviceId").value(deviceId))
                .andExpect(jsonPath("$.content[0].alertType").value("ANOMALY"))
                .andExpect(jsonPath("$.content[0].message").value("Invalid coordinates detected"))
                .andExpect(jsonPath("$.content[0].latitude").value(invalidLatitude))
                .andExpect(jsonPath("$.content[0].longitude").value(validLongitude))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.content[0].processorName").value("AnomDetProcessor"));
    }

    /**
     * Test Case 2: Invalid Longitude (< -180) - End-to-End HTTP Flow
     * 
     * Tests the complete pipeline for longitude validation:
     * 1. POST telemetry with invalid longitude (-200.0) with authentication
     * 2. Verify ANOMALY alert creation via HTTP GET with authentication
     */
    @Test
    void testInvalidLongitudeCreatesAnomalyAlert_EndToEnd() throws Exception {
        String deviceId = "invalid-lon-device";
        double validLatitude = 40.7128;
        double invalidLongitude = -200.0; // Invalid: longitude cannot be < -180

        // Step 1: POST invalid telemetry data via HTTP with authentication
        Map<String, Object> telemetryData = createTelemetryPayload(deviceId, validLatitude, invalidLongitude);

        mockMvc.perform(post("/api/v1/telemetry")
                .header("Authorization", "Bearer " + TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted());

        // Step 2: Wait for async anomaly detection processing
        waitForAsyncProcessing();

        // Step 3: GET alerts via HTTP with authentication to verify ANOMALY alert was created
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].deviceId").value(deviceId))
                .andExpect(jsonPath("$.content[0].alertType").value("ANOMALY"))
                .andExpect(jsonPath("$.content[0].message").value("Invalid coordinates detected"))
                .andExpect(jsonPath("$.content[0].latitude").value(validLatitude))
                .andExpect(jsonPath("$.content[0].longitude").value(invalidLongitude))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"));
    }

    /**
     * Test Case 3: Extreme Latitude (> 80) - End-to-End HTTP Flow
     * 
     * Tests the extreme latitude anomaly detection:
     * 1. POST telemetry with extreme latitude (85.0) with authentication
     * 2. Verify ANOMALY alert creation for extreme coordinates with authentication
     */
    @Test
    void testExtremeLatitudeCreatesAnomalyAlert_EndToEnd() throws Exception {
        String deviceId = "extreme-lat-device";
        double extremeLatitude = 85.0; // Extreme but valid latitude
        double validLongitude = -74.0060;

        // Step 1: POST extreme telemetry data via HTTP with authentication
        Map<String, Object> telemetryData = createTelemetryPayload(deviceId, extremeLatitude, validLongitude);

        mockMvc.perform(post("/api/v1/telemetry")
                .header("Authorization", "Bearer " + TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted());

        // Step 2: Wait for async anomaly detection processing
        waitForAsyncProcessing();

        // Step 3: GET alerts via HTTP with authentication to verify ANOMALY alert was created
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].deviceId").value(deviceId))
                .andExpect(jsonPath("$.content[0].alertType").value("ANOMALY"))
                .andExpect(jsonPath("$.content[0].message").value("Extreme location detected"))
                .andExpect(jsonPath("$.content[0].latitude").value(extremeLatitude))
                .andExpect(jsonPath("$.content[0].longitude").value(validLongitude))
                .andExpect(jsonPath("$.content[0].severity").value("LOW"));
    }

    /**
     * Test Case 4: Multiple Invalid Coordinates - End-to-End HTTP Flow
     * 
     * Tests that multiple invalid telemetry entries create multiple alerts:
     * 1. POST multiple invalid telemetry entries with authentication
     * 2. Verify multiple ANOMALY alerts are created with authentication
     */
    @Test
    void testMultipleInvalidCoordinatesCreateMultipleAlerts_EndToEnd() throws Exception {
        String deviceId = "multi-invalid-device";

        // First invalid entry: extreme latitude
        Map<String, Object> telemetryData1 = createTelemetryPayload(deviceId, 95.0, -74.0);

        mockMvc.perform(post("/api/v1/telemetry")
                .header("Authorization", "Bearer " + TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData1)))
                .andExpect(status().isAccepted());

        // Second invalid entry: extreme longitude  
        Map<String, Object> telemetryData2 = createTelemetryPayload(deviceId, 40.0, -200.0);

        mockMvc.perform(post("/api/v1/telemetry")
                .header("Authorization", "Bearer " + TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData2)))
                .andExpect(status().isAccepted());

        // Wait for processing
        waitForAsyncProcessing();

        // Verify both alerts were created
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].alertType").value("ANOMALY"))
                .andExpect(jsonPath("$.content[1].alertType").value("ANOMALY"));
    }

    /**
     * Test Case 5: Valid Coordinates Do Not Create Anomaly Alert - End-to-End HTTP Flow
     * 
     * Tests that valid telemetry data does not trigger anomaly alerts:
     * 1. POST valid telemetry data with authentication
     * 2. Verify no ANOMALY alerts are created (but geofence alerts may exist) with authentication
     */
    @Test
    void testValidCoordinatesDoNotCreateAnomalyAlert_EndToEnd() throws Exception {
        String deviceId = "valid-coords-device";
        double validLatitude = 40.7128; // NYC latitude
        double validLongitude = -74.0060; // NYC longitude

        // Step 1: POST valid telemetry data with authentication
        Map<String, Object> telemetryData = createTelemetryPayload(deviceId, validLatitude, validLongitude);

        mockMvc.perform(post("/api/v1/telemetry")
                .header("Authorization", "Bearer " + TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted());

        // Step 2: Wait for async processing
        waitForAsyncProcessing();

        // Step 3: GET alerts with authentication and verify no ANOMALY alerts exist
        // (Note: GEOFENCE alerts may exist for this location)
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.alertType == 'ANOMALY')]").isEmpty());
    }

    /**
     * Test Case 6: Geofence Alert vs Anomaly Alert - End-to-End HTTP Flow
     * 
     * Tests that geofence alerts are created but not anomaly alerts for valid coordinates in restricted areas:
     * 1. POST valid telemetry data in restricted area with authentication
     * 2. Verify GEOFENCE alert is created but not ANOMALY alert with authentication
     */
    @Test
    void testGeofenceAlertNotAnomalyAlert_EndToEnd() throws Exception {
        String deviceId = "geofence-device";
        double validLatitude = 40.7589; // Times Square latitude (restricted area)
        double validLongitude = -73.9851; // Times Square longitude (restricted area)

        // Step 1: POST valid telemetry data in restricted area with authentication
        Map<String, Object> telemetryData = createTelemetryPayload(deviceId, validLatitude, validLongitude);

        mockMvc.perform(post("/api/v1/telemetry")
                .header("Authorization", "Bearer " + TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted());

        // Step 2: Wait for async processing
        waitForAsyncProcessing();

        // Step 3: Verify GEOFENCE alert exists but no ANOMALY alert with authentication
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .header("Authorization", "Bearer " + TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.alertType == 'GEOFENCE')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.alertType == 'ANOMALY')]").isEmpty());
    }
} 