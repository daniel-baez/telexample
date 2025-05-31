package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.entities.Alert;
import cl.baezdaniel.telexample.repositories.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertService
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertService alertService;

    private String deviceId;
    private String alertType;
    private String message;
    private Double latitude;
    private Double longitude;
    private String processorName;
    private String metadata;

    @BeforeEach
    void setUp() {
        // Set up test data
        deviceId = "DEVICE001";
        alertType = "ANOMALY";
        message = "Test anomaly message";
        latitude = 40.7128;
        longitude = -74.0060;
        processorName = "TestProcessor";
        metadata = "{\"test\": \"data\"}";
        
        // Set retention months for testing
        ReflectionTestUtils.setField(alertService, "retentionMonths", 3);
    }

    @Test
    void testCreateAlert_Success() {
        // Given
        Alert mockAlert = new Alert(deviceId, alertType, "MEDIUM", message, latitude, longitude, processorName, "fingerprint123", metadata);
        mockAlert.setId(1L);
        
        when(alertRepository.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenReturn(mockAlert);

        // When
        Alert result = alertService.createAlert(deviceId, alertType, message, latitude, longitude, processorName, metadata);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDeviceId()).isEqualTo(deviceId);
        assertThat(result.getAlertType()).isEqualTo(alertType);
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getLatitude()).isEqualTo(latitude);
        assertThat(result.getLongitude()).isEqualTo(longitude);
        assertThat(result.getProcessorName()).isEqualTo(processorName);
        assertThat(result.getMetadata()).isEqualTo(metadata);
        
        verify(alertRepository).findByFingerprint(anyString());
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void testCreateAlert_NullDeviceId() {
        // When & Then
        assertThatThrownBy(() -> alertService.createAlert(null, alertType, message, latitude, longitude, processorName, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device ID cannot be null or empty");
        
        verifyNoInteractions(alertRepository);
    }

    @Test
    void testCreateAlert_EmptyDeviceId() {
        // When & Then
        assertThatThrownBy(() -> alertService.createAlert("", alertType, message, latitude, longitude, processorName, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device ID cannot be null or empty");
        
        verifyNoInteractions(alertRepository);
    }

    @Test
    void testCreateAlert_NullAlertType() {
        // When & Then
        assertThatThrownBy(() -> alertService.createAlert(deviceId, null, message, latitude, longitude, processorName, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alert type cannot be null or empty");
        
        verifyNoInteractions(alertRepository);
    }

    @Test
    void testCreateAlert_EmptyAlertType() {
        // When & Then
        assertThatThrownBy(() -> alertService.createAlert(deviceId, "", message, latitude, longitude, processorName, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alert type cannot be null or empty");
        
        verifyNoInteractions(alertRepository);
    }

    @Test
    void testCreateAlert_NullMessage() {
        // When & Then
        assertThatThrownBy(() -> alertService.createAlert(deviceId, alertType, null, latitude, longitude, processorName, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alert message cannot be null or empty");
        
        verifyNoInteractions(alertRepository);
    }

    @Test
    void testCreateAlert_EmptyMessage() {
        // When & Then
        assertThatThrownBy(() -> alertService.createAlert(deviceId, alertType, "", latitude, longitude, processorName, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alert message cannot be null or empty");
        
        verifyNoInteractions(alertRepository);
    }

    @Test
    void testCreateAlert_DuplicateAlert_ReturnsExisting() {
        // Given
        Alert existingAlert = new Alert(deviceId, alertType, "MEDIUM", message, latitude, longitude, processorName, "fingerprint123", metadata);
        existingAlert.setId(1L);
        
        when(alertRepository.findByFingerprint(anyString())).thenReturn(Optional.of(existingAlert));

        // When
        Alert result = alertService.createAlert(deviceId, alertType, message, latitude, longitude, processorName, metadata);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(existingAlert.getId());
        assertThat(result.getDeviceId()).isEqualTo(deviceId);
        
        verify(alertRepository).findByFingerprint(anyString());
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void testGetAlertsWithFilters_ValidFilters_Success() {
        // Given
        String testDeviceId = "DEVICE001";
        String testAlertType = "ANOMALY";
        String testSeverity = "HIGH";
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now();

        Pageable pageable = PageRequest.of(0, 10);
        Alert mockAlert = new Alert("DEVICE001", "ANOMALY", "HIGH", "Test message", 40.0, -74.0, "TestProcessor", "fingerprint", null);
        List<Alert> alerts = Arrays.asList(mockAlert);
        Page<Alert> alertPage = new PageImpl<>(alerts, pageable, alerts.size());

        when(alertRepository.findWithFilters(
            eq(testDeviceId), 
            eq(testAlertType), 
            eq(testSeverity), 
            eq(startDate), 
            eq(endDate), 
            same(pageable)))
            .thenReturn(alertPage);

        // When
        Page<Alert> result = alertService.getAlertsWithFilters(testDeviceId, testAlertType, testSeverity, startDate, endDate, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("DEVICE001", result.getContent().get(0).getDeviceId());
    }

    @Test
    void testGetAlertsWithFilters_EmptyFilters_ReturnsAll() {
        // Given - all filter parameters are null/empty
        String testDeviceId = null;
        String testAlertType = "";
        String testSeverity = null;
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;

        Pageable pageable = PageRequest.of(0, 10);
        Alert mockAlert = new Alert("DEVICE001", "ANOMALY", "HIGH", "Test message", 40.0, -74.0, "TestProcessor", "fingerprint", null);
        List<Alert> alerts = Arrays.asList(mockAlert);
        Page<Alert> alertPage = new PageImpl<>(alerts, pageable, alerts.size());

        when(alertRepository.findAll(same(pageable))).thenReturn(alertPage);

        // When
        Page<Alert> result = alertService.getAlertsWithFilters(testDeviceId, testAlertType, testSeverity, startDate, endDate, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertThat(result.getContent().get(0)).isNotNull();
        verify(alertRepository).findAll(same(pageable));
        verify(alertRepository, never()).findWithFilters(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getAlertById_ExistingId_Success() {
        // Given
        Alert mockAlert = new Alert("DEVICE001", "ANOMALY", "HIGH", "Test message", 40.0, -74.0, "TestProcessor", "fingerprint", null);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(mockAlert));

        // When
        Optional<Alert> result = alertService.getAlertById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("DEVICE001", result.get().getDeviceId());
    }

    @Test
    void getAlertById_NonExistingId_ReturnsEmpty() {
        // Given
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<Alert> result = alertService.getAlertById(999L);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void getAllAlerts_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Alert mockAlert = new Alert("DEVICE001", "ANOMALY", "HIGH", "Test message", 40.0, -74.0, "TestProcessor", "fingerprint", null);
        List<Alert> alerts = Arrays.asList(mockAlert);
        Page<Alert> alertPage = new PageImpl<>(alerts, pageable, alerts.size());

        when(alertRepository.findAll(pageable)).thenReturn(alertPage);

        // When
        Page<Alert> result = alertService.getAllAlerts(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("DEVICE001", result.getContent().get(0).getDeviceId());
    }

    @Test
    void cleanupOldAlerts_Success() {
        // Given
        when(alertRepository.countOlderThan(any(LocalDateTime.class))).thenReturn(5L);
        doNothing().when(alertRepository).deleteAlertsOlderThan(any(LocalDateTime.class));

        // When
        int deletedCount = alertService.cleanupOldAlerts();

        // Then
        assertEquals(5, deletedCount);
        verify(alertRepository).countOlderThan(any(LocalDateTime.class));
        verify(alertRepository).deleteAlertsOlderThan(any(LocalDateTime.class));
    }

    @Test
    void testFingerprintConsistency() {
        // Given - create two alerts with same parameters
        String testDeviceId = "DEVICE001";
        String testAlertType = "ANOMALY";
        String testMessage = "Test message";
        Double testLat = 10.0;
        Double testLon = 20.0;
        String testProcessor = "Processor";
        
        Alert mockAlert1 = new Alert(testDeviceId, testAlertType, "MEDIUM", testMessage, testLat, testLon, testProcessor, "fingerprint123", null);
        Alert mockAlert2 = new Alert(testDeviceId, testAlertType, "MEDIUM", testMessage, testLat, testLon, testProcessor, "fingerprint123", null);
        
        when(alertRepository.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenReturn(mockAlert1, mockAlert2);

        // When - create alerts with same parameters
        Alert alert1 = alertService.createAlert(testDeviceId, testAlertType, testMessage, testLat, testLon, testProcessor, null);
        Alert alert2 = alertService.createAlert(testDeviceId, testAlertType, testMessage, testLat, testLon, testProcessor, null);

        // Then - both alerts should have been created successfully
        assertThat(alert1).isNotNull();
        assertThat(alert2).isNotNull();
    }
} 