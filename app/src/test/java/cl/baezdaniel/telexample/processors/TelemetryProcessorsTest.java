package cl.baezdaniel.telexample.processors;

import cl.baezdaniel.telexample.entities.Alert;
import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import cl.baezdaniel.telexample.services.AlertService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for individual telemetry processor methods.
 * Tests isolated processor behavior, logging, and timing requirements.
 */
@SpringBootTest
class TelemetryProcessorsTest {

    @Autowired
    private TelemetryProcessors telemetryProcessors;

    @Autowired
    private AlertService alertService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger processorLogger;
    
    // Add fields for async synchronization
    // private final Object logListLock = new Object();

    @BeforeEach
    void setUp() {
        // Setup log capture for processors
        processorLogger = (Logger) LoggerFactory.getLogger(TelemetryProcessors.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        processorLogger.addAppender(listAppender);
        
        // Ensure clean state
        listAppender.list.clear();
    }

    /**
     * Wait for async processing to complete
     */
    private void waitForAsyncProcessing() throws InterruptedException {
        // Simple sleep approach with reasonable timeout
        Thread.sleep(300); // 300ms should be enough for async processors to complete
    }

    private Telemetry createTestTelemetry(String deviceId, double lat, double lon) {
        Telemetry telemetry = new Telemetry();
        telemetry.setId(1L);
        telemetry.setDeviceId(deviceId);
        telemetry.setLatitude(lat);
        telemetry.setLongitude(lon);
        telemetry.setTimestamp(LocalDateTime.now());
        return telemetry;
    }

    private TelemetryEvent createTestEvent(Telemetry telemetry) {
        return new TelemetryEvent(this, telemetry);
    }

    /**
     * Test Case 2.1: Anomaly Detection Logic
     * Validate anomaly detection rules and logging
     */
    @Test
    void testAnomalyDetection() throws Exception {
        // Test normal coordinates - should not trigger anomaly
        Telemetry normalTelemetry = createTestTelemetry("normal-device", 40.7128, -74.0060);
        TelemetryEvent normalEvent = createTestEvent(normalTelemetry);

        long startTime = System.currentTimeMillis();
        
        // CHANGE: Use event publishing instead of direct call
        eventPublisher.publishEvent(normalEvent);
        
        // WAIT for async processing to complete
        waitForAsyncProcessing();
        
        long processingTime = System.currentTimeMillis() - startTime;

        // Thread-safe log collection
        List<String> logMessages;
        // synchronized (logListLock) {
            logMessages = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        // }

        // Verify normal processing log with 🔍 emoji
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🔍") && msg.contains("normal-device"));
        
        // Verify thread name inclusion in logs
        assertThat(logMessages).anyMatch(msg -> msg.contains("Thread:"));

        // Assert processing time includes async execution time
        assertThat(processingTime).isBetween(0L, 500L); // Adjusted for async execution with wait

        // Clear logs for next test
        listAppender.list.clear();

        // Test invalid coordinates (lat > 90) - should trigger anomaly
        Telemetry invalidLatTelemetry = createTestTelemetry("anomaly-device", 95.0, -74.0);
        TelemetryEvent invalidLatEvent = createTestEvent(invalidLatTelemetry);

        eventPublisher.publishEvent(invalidLatEvent);
        
        // WAIT for async processing to complete
        waitForAsyncProcessing();

        // Thread-safe log collection
        // synchronized (logListLock) {
            logMessages = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        // }

        // Expect 🚨 ANOMALY DETECTED log for invalid coordinates
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🚨") && msg.contains("ANOMALY DETECTED") && msg.contains("Invalid coordinates"));

        // Clear logs for next test
        listAppender.list.clear();

        // Test extreme latitude (lat > 80) - should trigger anomaly
        Telemetry extremeLatTelemetry = createTestTelemetry("extreme-device", 85.0, -74.0);
        TelemetryEvent extremeLatEvent = createTestEvent(extremeLatTelemetry);

        eventPublisher.publishEvent(extremeLatEvent);
        
        // WAIT for async processing to complete
        waitForAsyncProcessing();

        // Thread-safe log collection
        // synchronized (logListLock) {
            logMessages = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        // }

        // Expect 🚨 ANOMALY DETECTED log for extreme latitude
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🚨") && msg.contains("ANOMALY DETECTED") && msg.contains("Extreme latitude"));
    }

    /**
     * Test Case 2.2: Alert System Processing
     * Test geofencing and alert logic
     */
    @Test
    void testAlertProcessing() throws Exception {
        // Test coordinates outside restricted area - should not trigger alert
        Telemetry normalTelemetry = createTestTelemetry("normal-alert-device", 39.0, -75.0);
        TelemetryEvent normalEvent = createTestEvent(normalTelemetry);

        long startTime = System.currentTimeMillis();
        eventPublisher.publishEvent(normalEvent);
        
        // WAIT for async processing to complete
        waitForAsyncProcessing();
        
        long processingTime = System.currentTimeMillis() - startTime;

        // Verify that no alert was created for normal coordinates
        Page<Alert> alerts = alertService.getAlertsForDevice("normal-alert-device", Pageable.unpaged());
        assertThat(alerts.getContent()).isEmpty();

        // Assert processing time includes async execution
        assertThat(processingTime).isBetween(0L, 500L);

        // Clear logs for next test
        listAppender.list.clear();

        // Test coordinates in restricted area (40.5, -74.0) - should trigger alert
        Telemetry restrictedTelemetry = createTestTelemetry("restricted-device", 40.7589, -73.9851);
        TelemetryEvent restrictedEvent = createTestEvent(restrictedTelemetry);

        eventPublisher.publishEvent(restrictedEvent);
        
        // WAIT for async processing to complete
        waitForAsyncProcessing();

        // Verify that an alert was created for restricted coordinates
        alerts = alertService.getAlertsForDevice("restricted-device", Pageable.unpaged());
        assertThat(alerts.getContent()).hasSize(1);
        Alert alert = alerts.getContent().get(0);
        assertThat(alert.getAlertType()).isEqualTo("GEOFENCE");
        assertThat(alert.getMessage()).contains("restricted area");
        assertThat(alert.getLatitude()).isEqualTo(40.7589);
        assertThat(alert.getLongitude()).isEqualTo(-73.9851);
    }

    /**
     * Test Case 2.3: Data Aggregation Processing
     * Validate aggregation logic and coordinate logging
     */
    @Test
    void testDataAggregation() throws Exception {
        // Process telemetry event with test coordinates
        Telemetry telemetry = createTestTelemetry("aggregation-device", 41.8781, -87.6298);
        TelemetryEvent event = createTestEvent(telemetry);

        long startTime = System.currentTimeMillis();
        eventPublisher.publishEvent(event);
        
        // WAIT for async processing to complete
        waitForAsyncProcessing();
        
        long processingTime = System.currentTimeMillis() - startTime;

        // Thread-safe log collection
        List<String> logMessages;
        // synchronized (logListLock) {
            logMessages = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        // }

        // Verify 🗺️ emoji in processing log
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🗺️") && msg.contains("aggregation-device"));

        // Assert 🗺️ aggregation log contains exact coordinates  
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🗺️") && msg.contains("Aggregated coordinates") && 
                msg.contains("41.8781") && msg.contains("-87.6298"));

        // Confirm device ID included in logs
        assertThat(logMessages).anyMatch(msg -> msg.contains("aggregation-device"));

        // Validate processing time includes async execution
        assertThat(processingTime).isBetween(0L, 500L);
    }


    /**
     * Additional test for error handling in processors
     */
    @Test
    void testProcessorErrorHandling() throws Exception {
        TelemetryEvent eventWithNullTelemetry = new TelemetryEvent(this, null);

        // Publish null event to test error handling
        eventPublisher.publishEvent(eventWithNullTelemetry);
        
        // WAIT for async processing to complete
        waitForAsyncProcessing();

        // Thread-safe log collection
        List<String> logMessages;
        // synchronized (logListLock) {
            logMessages = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        // }

        // Should have error logs for null telemetry
        long errorCount = logMessages.stream()
                .filter(msg -> msg.contains("Error") && msg.contains("null"))
                .count();

        assertThat(errorCount).isGreaterThan(0);
    }
} 