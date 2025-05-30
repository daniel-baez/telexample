package cl.baezdaniel.telexample.repositories;

import cl.baezdaniel.telexample.entities.Alert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AlertRepository JPA queries.
 * Tests repository layer functionality including custom queries and pagination.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertRepositoryTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // Clear database between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM alerts");
        }
    }

    private Alert createTestAlert(String deviceId, String alertType, String severity, double lat, double lon, LocalDateTime createdAt) {
        Alert alert = new Alert();
        alert.setDeviceId(deviceId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(String.format("Test %s alert for device %s", alertType, deviceId));
        alert.setLatitude(lat);
        alert.setLongitude(lon);
        alert.setProcessorName("TestProcessor");
        alert.setCreatedAt(createdAt);
        alert.setFingerprint(deviceId + alertType + System.nanoTime());
        alert.setMetadata("{\"test\": true}");
        return alertRepository.save(alert);
    }

    private Alert createTestAlert(String deviceId, String alertType, String severity) {
        return createTestAlert(deviceId, alertType, severity, 40.0, -74.0, LocalDateTime.now());
    }

    /**
     * Test Case 1: Basic CRUD operations
     */
    @Test
    void testBasicCrudOperations() {
        // Given
        Alert alert = new Alert();
        alert.setDeviceId("test-device");
        alert.setAlertType("ANOMALY");
        alert.setSeverity("HIGH");
        alert.setMessage("Test alert message");
        alert.setLatitude(40.7128);
        alert.setLongitude(-74.0060);
        alert.setProcessorName("TestProcessor");
        alert.setCreatedAt(LocalDateTime.now());
        alert.setFingerprint("unique-fingerprint");
        alert.setMetadata("{\"test\": true}");

        // When - Create
        Alert savedAlert = alertRepository.save(alert);

        // Then
        assertThat(savedAlert.getId()).isNotNull();
        assertThat(savedAlert.getDeviceId()).isEqualTo("test-device");

        // When - Read
        Optional<Alert> foundAlert = alertRepository.findById(savedAlert.getId());

        // Then
        assertThat(foundAlert).isPresent();
        assertThat(foundAlert.get().getDeviceId()).isEqualTo("test-device");

        // When - Update
        foundAlert.get().setSeverity("CRITICAL");
        Alert updatedAlert = alertRepository.save(foundAlert.get());

        // Then
        assertThat(updatedAlert.getSeverity()).isEqualTo("CRITICAL");

        // When - Delete
        alertRepository.delete(updatedAlert);

        // Then
        assertThat(alertRepository.findById(savedAlert.getId())).isEmpty();
    }

    /**
     * Test Case 2: Find by device ID
     */
    @Test
    void testFindByDeviceId() {
        // Given
        createTestAlert("device-1", "ANOMALY", "HIGH");
        createTestAlert("device-1", "SPEED", "MEDIUM");
        createTestAlert("device-2", "GEOFENCE", "LOW");

        // When
        List<Alert> device1Alerts = alertRepository.findByDeviceId("device-1");
        List<Alert> device2Alerts = alertRepository.findByDeviceId("device-2");
        List<Alert> nonExistentAlerts = alertRepository.findByDeviceId("non-existent");

        // Then
        assertThat(device1Alerts).hasSize(2);
        assertThat(device1Alerts.stream().map(Alert::getAlertType)).containsExactlyInAnyOrder("ANOMALY", "SPEED");
        assertThat(device2Alerts).hasSize(1);
        assertThat(device2Alerts.get(0).getAlertType()).isEqualTo("GEOFENCE");
        assertThat(nonExistentAlerts).isEmpty();
    }

    /**
     * Test Case 3: Find by device ID with ordering
     */
    @Test
    void testFindByDeviceIdOrderByCreatedAtDesc() {
        // Given
        LocalDateTime time1 = LocalDateTime.now().minusHours(2);
        LocalDateTime time2 = LocalDateTime.now().minusHours(1);
        LocalDateTime time3 = LocalDateTime.now();

        createTestAlert("device-1", "ANOMALY", "HIGH", 40.0, -74.0, time1);
        createTestAlert("device-1", "SPEED", "MEDIUM", 40.1, -74.1, time3);
        createTestAlert("device-1", "GEOFENCE", "LOW", 40.2, -74.2, time2);

        // When
        List<Alert> orderedAlerts = alertRepository.findByDeviceIdOrderByCreatedAtDesc("device-1");

        // Then
        assertThat(orderedAlerts).hasSize(3);
        assertThat(orderedAlerts.get(0).getAlertType()).isEqualTo("SPEED"); // Most recent
        assertThat(orderedAlerts.get(1).getAlertType()).isEqualTo("GEOFENCE"); // Middle
        assertThat(orderedAlerts.get(2).getAlertType()).isEqualTo("ANOMALY"); // Oldest
    }

    /**
     * Test Case 4: Pagination queries
     */
    @Test
    void testPaginationQueries() {
        // Given
        String deviceId = "pagination-device";
        for (int i = 0; i < 25; i++) {
            createTestAlert(deviceId, "ANOMALY", "HIGH");
        }

        Pageable firstPage = PageRequest.of(0, 10);
        Pageable secondPage = PageRequest.of(1, 10);
        Pageable lastPage = PageRequest.of(2, 10);

        // When
        Page<Alert> page1 = alertRepository.findByDeviceId(deviceId, firstPage);
        Page<Alert> page2 = alertRepository.findByDeviceId(deviceId, secondPage);
        Page<Alert> page3 = alertRepository.findByDeviceId(deviceId, lastPage);

        // Then
        assertThat(page1.getContent()).hasSize(10);
        assertThat(page1.getTotalElements()).isEqualTo(25);
        assertThat(page1.getTotalPages()).isEqualTo(3);
        assertThat(page1.isFirst()).isTrue();
        assertThat(page1.isLast()).isFalse();

        assertThat(page2.getContent()).hasSize(10);
        assertThat(page2.isFirst()).isFalse();
        assertThat(page2.isLast()).isFalse();

        assertThat(page3.getContent()).hasSize(5);
        assertThat(page3.isFirst()).isFalse();
        assertThat(page3.isLast()).isTrue();
    }

    /**
     * Test Case 5: Find by device ID and alert type with pagination
     */
    @Test
    void testFindByDeviceIdAndAlertType() {
        // Given
        String deviceId = "filter-device";
        createTestAlert(deviceId, "ANOMALY", "HIGH");
        createTestAlert(deviceId, "ANOMALY", "MEDIUM");
        createTestAlert(deviceId, "SPEED", "LOW");
        createTestAlert(deviceId, "GEOFENCE", "HIGH");

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> anomalyAlerts = alertRepository.findByDeviceIdAndAlertType(deviceId, "ANOMALY", pageable);
        Page<Alert> speedAlerts = alertRepository.findByDeviceIdAndAlertType(deviceId, "SPEED", pageable);

        // Then
        assertThat(anomalyAlerts.getContent()).hasSize(2);
        assertThat(anomalyAlerts.getContent().stream().map(Alert::getAlertType)).allMatch(type -> type.equals("ANOMALY"));
        
        assertThat(speedAlerts.getContent()).hasSize(1);
        assertThat(speedAlerts.getContent().get(0).getAlertType()).isEqualTo("SPEED");
    }

    /**
     * Test Case 6: Find by severity
     */
    @Test
    void testFindBySeverity() {
        // Given
        createTestAlert("device-1", "ANOMALY", "HIGH");
        createTestAlert("device-2", "SPEED", "HIGH");
        createTestAlert("device-3", "GEOFENCE", "LOW");
        createTestAlert("device-4", "ANOMALY", "MEDIUM");

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> highSeverityAlerts = alertRepository.findBySeverity("HIGH", pageable);
        Page<Alert> lowSeverityAlerts = alertRepository.findBySeverity("LOW", pageable);

        // Then
        assertThat(highSeverityAlerts.getContent()).hasSize(2);
        assertThat(highSeverityAlerts.getContent().stream().map(Alert::getSeverity)).allMatch(severity -> severity.equals("HIGH"));
        
        assertThat(lowSeverityAlerts.getContent()).hasSize(1);
        assertThat(lowSeverityAlerts.getContent().get(0).getSeverity()).isEqualTo("LOW");
    }

    /**
     * Test Case 7: Deduplication functionality
     */
    @Test
    void testDeduplicationQueries() {
        // Given
        String fingerprint = "unique-fingerprint-123";
        
        Alert alert = new Alert();
        alert.setDeviceId("test-device");
        alert.setAlertType("ANOMALY");
        alert.setSeverity("HIGH");
        alert.setMessage("Test message");
        alert.setLatitude(40.0);
        alert.setLongitude(-74.0);
        alert.setProcessorName("TestProcessor");
        alert.setCreatedAt(LocalDateTime.now());
        alert.setFingerprint(fingerprint);
        alert.setMetadata("{}");
        
        alertRepository.save(alert);

        // When & Then - findByFingerprint
        Optional<Alert> foundAlert = alertRepository.findByFingerprint(fingerprint);
        assertThat(foundAlert).isPresent();
        assertThat(foundAlert.get().getFingerprint()).isEqualTo(fingerprint);

        Optional<Alert> notFoundAlert = alertRepository.findByFingerprint("non-existent-fingerprint");
        assertThat(notFoundAlert).isEmpty();

        // When & Then - existsByFingerprint
        boolean exists = alertRepository.existsByFingerprint(fingerprint);
        assertThat(exists).isTrue();

        boolean notExists = alertRepository.existsByFingerprint("non-existent-fingerprint");
        assertThat(notExists).isFalse();
    }

    /**
     * Test Case 8: Date range queries
     */
    @Test
    void testDateRangeQueries() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(2);
        LocalDateTime endDate = now.plusDays(1);
        
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.0, -74.0, now.minusDays(3)); // Before range
        createTestAlert("device-1", "SPEED", "MEDIUM", 40.1, -74.1, now.minusDays(1)); // In range
        createTestAlert("device-1", "GEOFENCE", "LOW", 40.2, -74.2, now); // In range
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.3, -74.3, now.plusDays(2)); // After range

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Alert> alertsInRange = alertRepository.findByDeviceIdAndDateRange("device-1", startDate, endDate, pageable);

        // Then
        assertThat(alertsInRange.getContent()).hasSize(2);
        assertThat(alertsInRange.getContent().stream().map(Alert::getAlertType)).containsExactlyInAnyOrder("SPEED", "GEOFENCE");
    }

    /**
     * Test Case 9: Count queries
     */
    @Test
    void testCountQueries() {
        // Given
        createTestAlert("device-1", "ANOMALY", "HIGH");
        createTestAlert("device-1", "SPEED", "HIGH");
        createTestAlert("device-2", "GEOFENCE", "LOW");
        createTestAlert("device-3", "ANOMALY", "MEDIUM");

        // When & Then - countByDeviceId
        long device1Count = alertRepository.countByDeviceId("device-1");
        long device2Count = alertRepository.countByDeviceId("device-2");
        long nonExistentCount = alertRepository.countByDeviceId("non-existent");

        assertThat(device1Count).isEqualTo(2);
        assertThat(device2Count).isEqualTo(1);
        assertThat(nonExistentCount).isEqualTo(0);

        // When & Then - countBySeverity
        long highSeverityCount = alertRepository.countBySeverity("HIGH");
        long lowSeverityCount = alertRepository.countBySeverity("LOW");
        long criticalSeverityCount = alertRepository.countBySeverity("CRITICAL");

        assertThat(highSeverityCount).isEqualTo(2);
        assertThat(lowSeverityCount).isEqualTo(1);
        assertThat(criticalSeverityCount).isEqualTo(0);

        // When & Then - countByAlertType
        long anomalyCount = alertRepository.countByAlertType("ANOMALY");
        long speedCount = alertRepository.countByAlertType("SPEED");
        long geofenceCount = alertRepository.countByAlertType("GEOFENCE");

        assertThat(anomalyCount).isEqualTo(2);
        assertThat(speedCount).isEqualTo(1);
        assertThat(geofenceCount).isEqualTo(1);
    }

    /**
     * Test Case 10: Recent alerts queries
     */
    @Test
    void testRecentAlertsQueries() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(12);
        
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.0, -74.0, now.minusHours(24)); // Old
        createTestAlert("device-1", "SPEED", "MEDIUM", 40.1, -74.1, now.minusHours(6)); // Recent
        createTestAlert("device-2", "GEOFENCE", "LOW", 40.2, -74.2, now.minusHours(3)); // Recent
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.3, -74.3, now.minusHours(1)); // Recent

        // When & Then - findRecentAlerts
        List<Alert> recentAlerts = alertRepository.findRecentAlerts(since);
        assertThat(recentAlerts).hasSize(3);
        assertThat(recentAlerts.stream().map(Alert::getAlertType)).containsExactlyInAnyOrder("SPEED", "GEOFENCE", "ANOMALY");

        // When & Then - findRecentAlertsForDevice
        List<Alert> recentDevice1Alerts = alertRepository.findRecentAlertsForDevice("device-1", since);
        assertThat(recentDevice1Alerts).hasSize(2);
        assertThat(recentDevice1Alerts.stream().map(Alert::getAlertType)).containsExactlyInAnyOrder("SPEED", "ANOMALY");
    }

    /**
     * Test Case 11: Retention management queries
     */
    @Test
    void testRetentionQueries() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(3);
        
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.0, -74.0, cutoffDate.minusDays(1)); // Old
        createTestAlert("device-2", "SPEED", "MEDIUM", 40.1, -74.1, cutoffDate.minusDays(2)); // Old
        createTestAlert("device-3", "GEOFENCE", "LOW", 40.2, -74.2, cutoffDate.plusDays(1)); // Recent

        // When & Then - countOlderThan
        long oldAlertsCount = alertRepository.countOlderThan(cutoffDate);
        assertThat(oldAlertsCount).isEqualTo(2);

        // When & Then - deleteAlertsOlderThan
        alertRepository.deleteAlertsOlderThan(cutoffDate);
        
        // Verify deletion
        long remainingCount = alertRepository.count();
        assertThat(remainingCount).isEqualTo(1);
        
        List<Alert> remainingAlerts = alertRepository.findAll();
        assertThat(remainingAlerts.get(0).getAlertType()).isEqualTo("GEOFENCE");
    }

    /**
     * Test Case 12: Advanced filtering query
     */
    @Test
    void testAdvancedFilteringQuery() {
        // Given
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now().plusDays(1);
        
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.0, -74.0, LocalDateTime.now());
        createTestAlert("device-1", "SPEED", "MEDIUM", 40.1, -74.1, LocalDateTime.now());
        createTestAlert("device-2", "ANOMALY", "HIGH", 40.2, -74.2, LocalDateTime.now());
        createTestAlert("device-1", "ANOMALY", "LOW", 40.3, -74.3, LocalDateTime.now().minusDays(2)); // Outside date range

        Pageable pageable = PageRequest.of(0, 10);

        // When & Then - All filters applied
        Page<Alert> filteredAlerts = alertRepository.findWithFilters(
            "device-1", "ANOMALY", "HIGH", startDate, endDate, pageable);
        
        assertThat(filteredAlerts.getContent()).hasSize(1);
        Alert alert = filteredAlerts.getContent().get(0);
        assertThat(alert.getDeviceId()).isEqualTo("device-1");
        assertThat(alert.getAlertType()).isEqualTo("ANOMALY");
        assertThat(alert.getSeverity()).isEqualTo("HIGH");

        // When & Then - Partial filters (null deviceId)
        Page<Alert> partialFilteredAlerts = alertRepository.findWithFilters(
            null, "ANOMALY", "HIGH", startDate, endDate, pageable);
        
        assertThat(partialFilteredAlerts.getContent()).hasSize(2);
    }

    /**
     * Test Case 13: Sorting and complex pagination
     */
    @Test
    void testSortingAndComplexPagination() {
        // Given
        createTestAlert("device-1", "ANOMALY", "HIGH", 40.0, -74.0, LocalDateTime.now().minusHours(3));
        createTestAlert("device-2", "SPEED", "MEDIUM", 40.1, -74.1, LocalDateTime.now().minusHours(2));
        createTestAlert("device-3", "GEOFENCE", "LOW", 40.2, -74.2, LocalDateTime.now().minusHours(1));

        // When & Then - Sort by severity ascending
        Pageable severitySort = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "severity"));
        Page<Alert> sortedBySeverity = alertRepository.findAll(severitySort);
        
        assertThat(sortedBySeverity.getContent()).hasSize(3);
        assertThat(sortedBySeverity.getContent().get(0).getSeverity()).isEqualTo("HIGH");
        assertThat(sortedBySeverity.getContent().get(1).getSeverity()).isEqualTo("LOW");
        assertThat(sortedBySeverity.getContent().get(2).getSeverity()).isEqualTo("MEDIUM");

        // When & Then - Sort by createdAt descending
        Pageable dateSort = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> sortedByDate = alertRepository.findAll(dateSort);
        
        assertThat(sortedByDate.getContent()).hasSize(3);
        assertThat(sortedByDate.getContent().get(0).getAlertType()).isEqualTo("GEOFENCE"); // Most recent
        assertThat(sortedByDate.getContent().get(2).getAlertType()).isEqualTo("ANOMALY"); // Oldest
    }
} 