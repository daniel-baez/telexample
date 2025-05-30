package cl.baezdaniel.telexample.events;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.processors.TelemetryProcessors;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for async telemetry event processing.
 * Tests end-to-end workflow from API call to async processor execution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TelemetryEventProcessingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger processorLogger;

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
        }
        
        // Setup log capture for processors
        setupLogCapture();
    }

    private void setupLogCapture() {
        processorLogger = (Logger) LoggerFactory.getLogger(TelemetryProcessors.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        processorLogger.addAppender(listAppender);
    }

    private void waitForAsyncProcessing() {
        // Allow time for async processing to complete
    }

    private Map<String, Object> createTestTelemetryData(String deviceId, double lat, double lon) {
        Map<String, Object> telemetryData = new HashMap<>();
        telemetryData.put("deviceId", deviceId);
        telemetryData.put("latitude", lat);
        telemetryData.put("longitude", lon);
        telemetryData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return telemetryData;
    }

    /**
     * Test Case 1.1: Complete Async Processing Flow
     * Verify that posting telemetry triggers all four processors asynchronously
     */
    @Test
    void testCompleteAsyncProcessingFlow() throws Exception {
        // POST valid telemetry data
        Map<String, Object> telemetryData = createTestTelemetryData("test-device-001", 40.7128, -74.0060);

        // Verify immediate API response (201 Created)
        mockMvc.perform(post("/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.deviceId").value("test-device-001"));

        // Wait for async processing completion
        waitForAsyncProcessing();

        // Get captured log events
        List<ILoggingEvent> logEvents = listAppender.list;
        List<String> logMessages = logEvents.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Assert all four processors executed (check logs for emojis)
        assertThat(logMessages).anyMatch(msg -> msg.contains("🔍") && msg.contains("test-device-001")); // Anomaly detection
        assertThat(logMessages).anyMatch(msg -> msg.contains("📊") && msg.contains("test-device-001")); // Statistics
        assertThat(logMessages).anyMatch(msg -> msg.contains("🔔") && msg.contains("test-device-001")); // Alerts
        assertThat(logMessages).anyMatch(msg -> msg.contains("🗺️") && msg.contains("test-device-001")); // Aggregation

        // Verify thread names contain "TelemetryProcessor-"
        assertThat(logMessages).anyMatch(msg -> msg.contains("TelemetryProcessor-"));

        // Confirm processing happened (at least 4 processor invocations)
        long processorExecutions = logMessages.stream()
                .filter(msg -> msg.contains("Processing") || msg.contains("updated") || msg.contains("aggregated"))
                .count();
        assertThat(processorExecutions).isGreaterThanOrEqualTo(4);
    }

    /**
     * Test Case 1.2: Multiple Concurrent Events
     * Validate thread pool handles multiple simultaneous events
     */
    @Test
    void testMultipleConcurrentEvents() throws Exception {
        final int numberOfEvents = 10;
        final CountDownLatch latch = new CountDownLatch(numberOfEvents);

        // Submit 10 telemetry records simultaneously using CompletableFuture
        List<CompletableFuture<Void>> futures = IntStream.range(0, numberOfEvents)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, Object> telemetryData = createTestTelemetryData(
                                "concurrent-device-" + i, 
                                40.0 + i * 0.1, 
                                -74.0 + i * 0.1
                        );

                        long startTime = System.currentTimeMillis();
                        
                        // Verify all API calls return 201 Created quickly (< 100ms each)
                        mockMvc.perform(post("/telemetry")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(telemetryData)))
                                .andExpect(status().isCreated());
                        
                        long responseTime = System.currentTimeMillis() - startTime;
                        assertThat(responseTime).isLessThan(100L);
                        
                        latch.countDown();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }))
                .toList();

        // Wait for all API calls to complete
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Wait for all async processing to complete

        // Get captured log events
        List<ILoggingEvent> logEvents = listAppender.list;
        List<String> logMessages = logEvents.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Assert all 40 processor executions occurred (10 × 4 processors)
        long anomalyExecutions = logMessages.stream().filter(msg -> msg.contains("🔍")).count();
        long statisticsExecutions = logMessages.stream().filter(msg -> msg.contains("📊")).count();
        long alertExecutions = logMessages.stream().filter(msg -> msg.contains("🔔")).count();
        long aggregationExecutions = logMessages.stream().filter(msg -> msg.contains("🗺️")).count();

        assertThat(anomalyExecutions).isEqualTo(numberOfEvents);
        assertThat(statisticsExecutions).isEqualTo(numberOfEvents);
        assertThat(alertExecutions).isEqualTo(numberOfEvents);
        assertThat(aggregationExecutions).isEqualTo(numberOfEvents);

        // Verify thread pool utilization (multiple thread names)
        long uniqueThreadNames = logMessages.stream()
                .filter(msg -> msg.contains("TelemetryProcessor-"))
                .map(msg -> {
                    int start = msg.indexOf("TelemetryProcessor-");
                    if (start == -1) return "";
                    int end = msg.indexOf("]", start);
                    return end == -1 ? "" : msg.substring(start, end);
                })
                .distinct()
                .count();
        
        assertThat(uniqueThreadNames).isGreaterThan(1); // Multiple threads were used

        // Confirm no events were lost
        long totalProcessorExecutions = anomalyExecutions + statisticsExecutions + alertExecutions + aggregationExecutions;
        assertThat(totalProcessorExecutions).isEqualTo(numberOfEvents * 4);
    }

    /**
     * Test Case 1.3: Event Data Integrity
     * Ensure telemetry data is correctly passed through events
     */
    @Test
    void testEventDataIntegrity() throws Exception {
        // POST telemetry with specific test coordinates
        final double testLat = 40.123;
        final double testLon = -74.456;
        final String testDeviceId = "data-integrity-device";
        
        Map<String, Object> telemetryData = createTestTelemetryData(testDeviceId, testLat, testLon);
        
        long eventCreationTime = System.currentTimeMillis();

        mockMvc.perform(post("/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isCreated());

        // Wait for async processing
        waitForAsyncProcessing();

        // Capture logs from all processors
        List<ILoggingEvent> logEvents = listAppender.list;
        List<String> logMessages = logEvents.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        // Verify each processor received correct device ID and coordinates
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🔍") && msg.contains(testDeviceId));
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("📊") && msg.contains(testDeviceId));
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🔔") && msg.contains(testDeviceId));
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("🗺️") && msg.contains(testDeviceId));

        // Verify coordinates are present in aggregation logs
        assertThat(logMessages).anyMatch(msg -> 
                msg.contains("📍") && msg.contains(String.valueOf(testLat)) && msg.contains(String.valueOf(testLon)));

        // Assert processing start time is accurate (events should be processed within reasonable time)
        long processingCompletionTime = System.currentTimeMillis();
        long totalProcessingTime = processingCompletionTime - eventCreationTime;
        assertThat(totalProcessingTime).isLessThan(2000L); // Should complete within 2 seconds

        // Confirm event immutability (device ID appears consistently across all processors)
        long deviceIdOccurrences = logMessages.stream()
                .filter(msg -> msg.contains(testDeviceId))
                .count();
        assertThat(deviceIdOccurrences).isGreaterThanOrEqualTo(4); // At least once per processor
    }
} 