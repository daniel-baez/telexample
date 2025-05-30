package cl.baezdaniel.telexample.processors;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

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
    private ApplicationEventPublisher eventPublisher;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger processorLogger;

    @BeforeEach
    void setUp() {
        // Setup log capture for processors
        processorLogger = (Logger) LoggerFactory.getLogger(TelemetryProcessors.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        processorLogger.addAppender(listAppender);
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
        
        // Wait for async processing to complete
        Thread.sleep(200); // Allow time for async execution
        
        long processingTime = System.currentTimeMillis() - startTime;

        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Verify normal processing log with 🔍 emoji
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🔍") && msg.contains("normal-device"));
        
        // Verify thread name inclusion in logs
        assertThat(logMessages).anyMatch(msg -> msg.contains("Thread:"));

        // Assert processing time includes async execution time
        assertThat(processingTime).isBetween(150L, 300L); // Adjusted for async execution

        // Clear logs for next test
        listAppender.list.clear();

        // Test invalid coordinates (lat > 90) - should trigger anomaly
        Telemetry invalidLatTelemetry = createTestTelemetry("anomaly-device", 95.0, -74.0);
        TelemetryEvent invalidLatEvent = createTestEvent(invalidLatTelemetry);

        eventPublisher.publishEvent(invalidLatEvent);
        Thread.sleep(200);

        logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Expect 🚨 ANOMALY DETECTED log for invalid coordinates
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🚨") && msg.contains("ANOMALY DETECTED") && msg.contains("Invalid coordinates"));

        // Clear logs for next test
        listAppender.list.clear();

        // Test extreme latitude (lat > 80) - should trigger anomaly
        Telemetry extremeLatTelemetry = createTestTelemetry("extreme-device", 85.0, -74.0);
        TelemetryEvent extremeLatEvent = createTestEvent(extremeLatTelemetry);

        eventPublisher.publishEvent(extremeLatEvent);
        Thread.sleep(200);

        logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Expect 🚨 ANOMALY DETECTED log for extreme latitude
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🚨") && msg.contains("ANOMALY DETECTED") && msg.contains("Extreme latitude"));
    }

    /**
     * Test Case 2.2: Statistics Processing
     * Verify statistics calculation and metrics logging
     */
    @Test
    void testStatisticsProcessing() throws Exception {
        // Create TelemetryEvent with known processing start time
        Telemetry telemetry = createTestTelemetry("stats-device", 40.7128, -74.0060);
        TelemetryEvent event = createTestEvent(telemetry);

        // Wait a bit to ensure processing delay calculation
        Thread.sleep(10);

        long startTime = System.currentTimeMillis();
        eventPublisher.publishEvent(event);
        Thread.sleep(200); // Wait for async processing
        long processingTime = System.currentTimeMillis() - startTime;

        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Verify 📊 emoji in logs with correct thread name
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("📊") && msg.contains("Thread:") && msg.contains("stats-device"));

        // Confirm 📈 statistics update log with device ID
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("📈") && msg.contains("Statistics updated") && msg.contains("stats-device"));

        // Assert processing delay calculation accuracy (should be > 0 since we waited)
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("processing delay:") && msg.contains("ms"));

        // Validate processing time includes async execution
        assertThat(processingTime).isBetween(150L, 300L);
    }

    /**
     * Test Case 2.3: Alert System Processing
     * Test geofencing and alert logic
     */
    @Test
    void testAlertProcessing() throws Exception {
        // Test coordinates outside restricted area - should not trigger alert
        Telemetry normalTelemetry = createTestTelemetry("normal-alert-device", 39.0, -75.0);
        TelemetryEvent normalEvent = createTestEvent(normalTelemetry);

        long startTime = System.currentTimeMillis();
        eventPublisher.publishEvent(normalEvent);
        Thread.sleep(200); // Wait for async processing
        long processingTime = System.currentTimeMillis() - startTime;

        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Expect normal 🔔 processing log
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🔔") && msg.contains("normal-alert-device"));

        // Assert processing time includes async execution
        assertThat(processingTime).isBetween(150L, 300L);

        // Clear logs for next test
        listAppender.list.clear();

        // Test coordinates in restricted area (40.5, -74.0) - should trigger alert
        Telemetry restrictedTelemetry = createTestTelemetry("restricted-device", 40.5, -74.0);
        TelemetryEvent restrictedEvent = createTestEvent(restrictedTelemetry);

        eventPublisher.publishEvent(restrictedEvent);
        Thread.sleep(200);

        logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Expect 🚨 ALERT log for restricted area
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🚨") && msg.contains("ALERT") && msg.contains("restricted area"));

        // Verify alert message includes device ID and coordinates
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("restricted-device") && msg.contains("40.5") && msg.contains("-74.0"));
    }

    /**
     * Test Case 2.4: Data Aggregation Processing
     * Validate aggregation logic and coordinate logging
     */
    @Test
    void testDataAggregation() throws Exception {
        // Process telemetry event with test coordinates
        Telemetry telemetry = createTestTelemetry("aggregation-device", 41.8781, -87.6298);
        TelemetryEvent event = createTestEvent(telemetry);

        long startTime = System.currentTimeMillis();
        eventPublisher.publishEvent(event);
        Thread.sleep(200); // Wait for async processing
        long processingTime = System.currentTimeMillis() - startTime;

        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Verify 🗺️ emoji in processing log
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🗺️") && msg.contains("aggregation-device"));

        // Assert 📍 aggregation log contains exact coordinates
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("📍") && msg.contains("Data aggregated") && 
                msg.contains("41.8781") && msg.contains("-87.6298"));

        // Confirm device ID included in logs
        assertThat(logMessages).anyMatch(msg -> msg.contains("aggregation-device"));

        // Validate processing time includes async execution
        assertThat(processingTime).isBetween(150L, 300L);
    }

    /**
     * Additional test for error handling in processors
     */
    @Test
    void testProcessorErrorHandling() throws Exception {
        TelemetryEvent eventWithNullTelemetry = new TelemetryEvent(this, null);

        // Publish null event to test error handling
        eventPublisher.publishEvent(eventWithNullTelemetry);
        
        // Wait for async processing
        Thread.sleep(100);

        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Should have error logs for null telemetry
        long errorCount = logMessages.stream()
                .filter(msg -> msg.contains("Error") && msg.contains("null"))
                .count();

        assertThat(errorCount).isGreaterThan(0);
    }
} 