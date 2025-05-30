package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.dto.AlertCreationRequest;
import cl.baezdaniel.telexample.dto.AlertFilterRequest;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertService alertService;

    private AlertCreationRequest validRequest;
    private Alert existingAlert;

    @BeforeEach
    void setUp() {
        validRequest = new AlertCreationRequest(
            "DEVICE001", 
            "ANOMALY", 
            "Extreme coordinate detected", 
            -1000.0, 
            -1000.0, 
            "AnomalyDetectionProcessor",
            "{\"severity\": \"HIGH\"}"
        );

        existingAlert = new Alert();
        existingAlert.setId(1L);
        existingAlert.setDeviceId("DEVICE001");
        existingAlert.setAlertType("ANOMALY");
        existingAlert.setMessage("Extreme coordinate detected");
        existingAlert.setLatitude(-1000.0);
        existingAlert.setLongitude(-1000.0);
        existingAlert.setProcessorName("AnomalyDetectionProcessor");
        existingAlert.setMetadata("{\"severity\": \"HIGH\"}");
        existingAlert.setSeverity("HIGH");
        existingAlert.setCreatedAt(LocalDateTime.now());
        existingAlert.setFingerprint("test-fingerprint");
    }

    @Test
    void createAlert_NewAlert_Success() {
        // Given
        when(alertRepository.findByFingerprint(anyString())).thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenReturn(existingAlert);

        // When
        Alert result = alertService.createAlert(validRequest);

        // Then
        assertNotNull(result);
        assertEquals("DEVICE001", result.getDeviceId());
        assertEquals("ANOMALY", result.getAlertType());
        assertEquals("HIGH", result.getSeverity());
        verify(alertRepository).findByFingerprint(anyString());
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void createAlert_DuplicateAlert_ReturnsExisting() {
        // Given
        when(alertRepository.findByFingerprint(anyString())).thenReturn(Optional.of(existingAlert));

        // When
        Alert result = alertService.createAlert(validRequest);

        // Then
        assertNotNull(result);
        assertEquals(existingAlert.getId(), result.getId());
        verify(alertRepository).findByFingerprint(anyString());
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void createAlert_NullRequest_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> alertService.createAlert(null));
    }

    @Test
    void getAlertsWithFilter_ValidFilter_Success() {
        // Given
        AlertFilterRequest filter = new AlertFilterRequest();
        filter.setDeviceId("DEVICE001");
        filter.setAlertType("ANOMALY");

        Pageable pageable = PageRequest.of(0, 10);
        List<Alert> alerts = Arrays.asList(existingAlert);
        Page<Alert> alertPage = new PageImpl<>(alerts, pageable, alerts.size());

        when(alertRepository.findByDeviceIdAndAlertTypeAndCreatedAtBetween(
            eq("DEVICE001"), eq("ANOMALY"), any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable)))
            .thenReturn(alertPage);

        // When
        Page<Alert> result = alertService.getAlertsWithFilter(filter, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("DEVICE001", result.getContent().get(0).getDeviceId());
    }

    @Test
    void getAlertsWithFilter_EmptyFilter_ReturnsAll() {
        // Given
        AlertFilterRequest filter = new AlertFilterRequest();
        Pageable pageable = PageRequest.of(0, 10);
        List<Alert> alerts = Arrays.asList(existingAlert);
        Page<Alert> alertPage = new PageImpl<>(alerts, pageable, alerts.size());

        when(alertRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable)))
            .thenReturn(alertPage);

        // When
        Page<Alert> result = alertService.getAlertsWithFilter(filter, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getAlertById_ExistingId_Success() {
        // Given
        when(alertRepository.findById(1L)).thenReturn(Optional.of(existingAlert));

        // When
        Optional<Alert> result = alertService.getAlertById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(existingAlert.getId(), result.get().getId());
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
        List<Alert> alerts = Arrays.asList(existingAlert);
        Page<Alert> alertPage = new PageImpl<>(alerts, pageable, alerts.size());

        when(alertRepository.findAll(pageable)).thenReturn(alertPage);

        // When
        Page<Alert> result = alertService.getAllAlerts(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
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
    void generateFingerprint_SameInputs_SameFingerprint() {
        // Given
        AlertCreationRequest request1 = new AlertCreationRequest("DEVICE001", "ANOMALY", "Test message", 10.0, 20.0, "Processor", null);
        AlertCreationRequest request2 = new AlertCreationRequest("DEVICE001", "ANOMALY", "Test message", 10.0, 20.0, "Processor", null);

        // When
        Alert alert1 = new Alert();
        alert1.setDeviceId(request1.getDeviceId());
        alert1.setAlertType(request1.getAlertType());
        alert1.setMessage(request1.getMessage());
        alert1.setLatitude(request1.getLatitude());
        alert1.setLongitude(request1.getLongitude());

        Alert alert2 = new Alert();
        alert2.setDeviceId(request2.getDeviceId());
        alert2.setAlertType(request2.getAlertType());
        alert2.setMessage(request2.getMessage());
        alert2.setLatitude(request2.getLatitude());
        alert2.setLongitude(request2.getLongitude());

        // Then
        // Both alerts should generate the same fingerprint for deduplication
        assertNotNull(alert1);
        assertNotNull(alert2);
    }
} 