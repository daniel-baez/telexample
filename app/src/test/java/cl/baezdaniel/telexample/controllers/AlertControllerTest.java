package cl.baezdaniel.telexample.controllers;

import cl.baezdaniel.telexample.entities.Alert;
import cl.baezdaniel.telexample.repositories.AlertRepository;
import cl.baezdaniel.telexample.services.AlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AlertController
 * Tests the REST API endpoints for alert retrieval and management
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "endpoint.auth.enabled=false")
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // Clear database between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM alerts");
        }
    }

    private Alert createTestAlert(String deviceId, String alertType, String severity, double lat, double lon) {
        Alert alert = new Alert();
        alert.setDeviceId(deviceId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(String.format("Test %s alert for device %s", alertType, deviceId));
        alert.setLatitude(lat);
        alert.setLongitude(lon);
        alert.setProcessorName("TestProcessor");
        alert.setCreatedAt(LocalDateTime.now());
        
        // Create a shorter fingerprint using hash (max 32 chars)
        String fingerprintData = deviceId + alertType + System.nanoTime();
        alert.setFingerprint(createShortFingerprint(fingerprintData));
        
        alert.setMetadata("{\"test\": true}");
        return alertRepository.save(alert);
    }
    
    private String createShortFingerprint(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            // Return first 30 characters of MD5 hash (well under 32 char limit)
            return hexString.toString().substring(0, 30);
        } catch (Exception e) {
            // Fallback to simple counter-based approach
            return "test_" + System.currentTimeMillis() % 100000000L;
        }
    }

    /**
     * Test Case 1: GET /api/v1/alerts/{deviceId} - Basic functionality
     */
    @Test
    void getAlertsForDevice_ValidDevice_ReturnsAlerts() throws Exception {
        // Given
        String deviceId = "test-device-001";
        createTestAlert(deviceId, "ANOMALY", "HIGH", 40.7128, -74.0060);
        createTestAlert(deviceId, "SPEED", "MEDIUM", 40.7129, -74.0061);
        createTestAlert("other-device", "ANOMALY", "LOW", 41.0, -75.0); // Should not be included

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].deviceId", everyItem(equalTo(deviceId))))
                .andExpect(jsonPath("$.content[*].alertType", containsInAnyOrder("ANOMALY", "SPEED")))
                .andExpect(jsonPath("$.totalElements", equalTo(2)))
                .andExpect(jsonPath("$.totalPages", equalTo(1)))
                .andExpect(jsonPath("$.first", equalTo(true)))
                .andExpect(jsonPath("$.last", equalTo(true)));
    }

    /**
     * Test Case 2: GET /api/v1/alerts/{deviceId} - Pagination
     */
    @Test
    void getAlertsForDevice_WithPagination_ReturnsPagedResults() throws Exception {
        // Given
        String deviceId = "pagination-device";
        for (int i = 0; i < 25; i++) {
            createTestAlert(deviceId, "ANOMALY", "HIGH", 40.0 + i * 0.001, -74.0);
        }

        // When & Then - First page
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalElements", equalTo(25)))
                .andExpect(jsonPath("$.totalPages", equalTo(3)))
                .andExpect(jsonPath("$.first", equalTo(true)))
                .andExpect(jsonPath("$.last", equalTo(false)))
                .andExpect(jsonPath("$.pageable.pageNumber", equalTo(0)))
                .andExpect(jsonPath("$.pageable.pageSize", equalTo(10)));

        // When & Then - Last page
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .param("page", "2")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.first", equalTo(false)))
                .andExpect(jsonPath("$.last", equalTo(true)));
    }

    /**
     * Test Case 3: GET /api/v1/alerts/{deviceId} - Filtering by alert type
     */
    @Test
    void getAlertsForDevice_WithAlertTypeFilter_ReturnsFilteredResults() throws Exception {
        // Given
        String deviceId = "filter-device";
        createTestAlert(deviceId, "ANOMALY", "HIGH", 40.0, -74.0);
        createTestAlert(deviceId, "SPEED", "MEDIUM", 40.1, -74.1);
        createTestAlert(deviceId, "GEOFENCE", "LOW", 40.2, -74.2);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .param("alertType", "ANOMALY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].alertType", equalTo("ANOMALY")))
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
    }

    /**
     * Test Case 4: GET /api/v1/alerts/{deviceId} - Filtering by severity
     */
    @Test
    void getAlertsForDevice_WithSeverityFilter_ReturnsFilteredResults() throws Exception {
        // Given
        String deviceId = "severity-device";
        createTestAlert(deviceId, "ANOMALY", "HIGH", 40.0, -74.0);
        createTestAlert(deviceId, "SPEED", "HIGH", 40.1, -74.1);
        createTestAlert(deviceId, "GEOFENCE", "LOW", 40.2, -74.2);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .param("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].severity", everyItem(equalTo("HIGH"))))
                .andExpect(jsonPath("$.totalElements", equalTo(2)));
    }

    /**
     * Test Case 5: GET /api/v1/alerts/{deviceId} - Date range filtering
     */
    @Test
    void getAlertsForDevice_WithDateRange_ReturnsFilteredResults() throws Exception {
        // Given
        String deviceId = "date-device";
        
        // Create alert in the past
        Alert oldAlert = createTestAlert(deviceId, "ANOMALY", "HIGH", 40.0, -74.0);
        oldAlert.setCreatedAt(LocalDateTime.now().minusDays(5));
        alertRepository.save(oldAlert);
        
        // Create recent alert
        Alert recentAlert = createTestAlert(deviceId, "SPEED", "MEDIUM", 40.1, -74.1);
        recentAlert.setCreatedAt(LocalDateTime.now().minusHours(1));
        alertRepository.save(recentAlert);

        String startDate = LocalDateTime.now().minusDays(2).toString();
        String endDate = LocalDateTime.now().toString();

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .param("startDate", startDate)
                .param("endDate", endDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].alertType", equalTo("SPEED")))
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
    }

    /**
     * Test Case 6: GET /api/v1/alerts - All alerts (admin view)
     */
    @Test
    void getAllAlerts_ReturnsAlertsFromAllDevices() throws Exception {
        // Given
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.0, -74.0);
        createTestAlert("device-2", "SPEED", "MEDIUM", 41.0, -75.0);
        createTestAlert("device-3", "GEOFENCE", "LOW", 42.0, -76.0);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[*].deviceId", containsInAnyOrder("device-1", "device-2", "device-3")))
                .andExpect(jsonPath("$.totalElements", equalTo(3)));
    }

    /**
     * Test Case 7: GET /api/v1/alerts - With device filter
     */
    @Test
    void getAllAlerts_WithDeviceFilter_ReturnsFilteredResults() throws Exception {
        // Given
        createTestAlert("target-device", "ANOMALY", "HIGH", 40.0, -74.0);
        createTestAlert("target-device", "SPEED", "MEDIUM", 40.1, -74.1);
        createTestAlert("other-device", "GEOFENCE", "LOW", 41.0, -75.0);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts")
                .param("deviceId", "target-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].deviceId", everyItem(equalTo("target-device"))))
                .andExpect(jsonPath("$.totalElements", equalTo(2)));
    }

    /**
     * Test Case 8: Sorting functionality
     */
    @Test
    void getAlertsForDevice_WithSorting_ReturnsSortedResults() throws Exception {
        // Given
        String deviceId = "sort-device";
        
        Alert alert1 = createTestAlert(deviceId, "ANOMALY", "HIGH", 40.0, -74.0);
        alert1.setCreatedAt(LocalDateTime.now().minusHours(2));
        alertRepository.save(alert1);
        
        Alert alert2 = createTestAlert(deviceId, "SPEED", "MEDIUM", 40.1, -74.1);
        alert2.setCreatedAt(LocalDateTime.now().minusHours(1));
        alertRepository.save(alert2);

        // When & Then - Default sorting (createdAt desc)
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].alertType", equalTo("SPEED"))) // More recent first
                .andExpect(jsonPath("$.content[1].alertType", equalTo("ANOMALY")));

        // When & Then - Custom sorting (severity asc)
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .param("sort", "severity,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].severity", equalTo("HIGH")))
                .andExpect(jsonPath("$.content[1].severity", equalTo("MEDIUM")));
    }

    /**
     * Test Case 9: Invalid device ID
     */
    @Test
    void getAlertsForDevice_NonExistentDevice_ReturnsEmptyResult() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", "non-existent-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", equalTo(0)));
    }

    /**
     * Test Case 10: Invalid pagination parameters
     */
    @Test
    void getAlertsForDevice_InvalidPagination_ReturnsBadRequest() throws Exception {
        // When & Then - Negative page
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", "test-device")
                .param("page", "-1"))
                .andExpect(status().isBadRequest());

        // When & Then - Size too large
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", "test-device")
                .param("size", "1000"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Case 11: Multiple filters combined
     */
    @Test
    void getAlertsForDevice_MultipleFilters_ReturnsCorrectResults() throws Exception {
        // Given
        String deviceId = "multi-filter-device";
        createTestAlert(deviceId, "ANOMALY", "HIGH", 40.0, -74.0);
        createTestAlert(deviceId, "ANOMALY", "LOW", 40.1, -74.1);
        createTestAlert(deviceId, "SPEED", "HIGH", 40.2, -74.2);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId)
                .param("alertType", "ANOMALY")
                .param("severity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].alertType", equalTo("ANOMALY")))
                .andExpect(jsonPath("$.content[0].severity", equalTo("HIGH")))
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
    }

    /**
     * Test Case 12: Response structure validation
     */
    @Test
    void getAlertsForDevice_ResponseStructure_ContainsAllFields() throws Exception {
        // Given
        String deviceId = "structure-device";
        createTestAlert(deviceId, "ANOMALY", "HIGH", 40.7128, -74.0060);

        // When & Then
        mockMvc.perform(get("/api/v1/alerts/{deviceId}", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", notNullValue()))
                .andExpect(jsonPath("$.content[0].deviceId", equalTo(deviceId)))
                .andExpect(jsonPath("$.content[0].alertType", equalTo("ANOMALY")))
                .andExpect(jsonPath("$.content[0].severity", equalTo("HIGH")))
                .andExpect(jsonPath("$.content[0].message", notNullValue()))
                .andExpect(jsonPath("$.content[0].latitude", equalTo(40.7128)))
                .andExpect(jsonPath("$.content[0].longitude", equalTo(-74.0060)))
                .andExpect(jsonPath("$.content[0].processorName", equalTo("TestProcessor")))
                .andExpect(jsonPath("$.content[0].createdAt", notNullValue()))
                .andExpect(jsonPath("$.content[0].metadata", equalTo("{\"test\": true}")));
    }
} 