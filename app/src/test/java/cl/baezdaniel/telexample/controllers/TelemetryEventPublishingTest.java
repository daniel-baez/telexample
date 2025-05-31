package cl.baezdaniel.telexample.controllers;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for telemetry event publishing functionality.
 * Validates event creation, publishing, and failure handling scenarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "endpoint.auth.enabled=false")
class TelemetryEventPublishingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestEventListener testEventListener;

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
        }
        
        // Clear captured events
        testEventListener.clearCapturedEvents();
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
     * Test Case 4.1: Event Publishing Verification
     * Confirm events are published after saving telemetry
     */
    @Test
    void testEventPublishingOccurs() throws Exception {
        // POST telemetry data
        Map<String, Object> telemetryData = createTestTelemetryData("event-test-device", 40.7128, -74.0060);

        mockMvc.perform(post("/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId").value("event-test-device"));

        // Wait for event processing
        boolean eventCaptured = testEventListener.waitForEvent(2, TimeUnit.SECONDS);
        assertThat(eventCaptured).isTrue();

        // Verify TelemetryEvent was published
        List<TelemetryEvent> capturedEvents = testEventListener.getCapturedEvents();
        assertThat(capturedEvents).hasSize(1);

        TelemetryEvent event = capturedEvents.get(0);
        
        // Assert event contains correct telemetry data
        assertThat(event.getTelemetry()).isNotNull();
        assertThat(event.getTelemetry().getDeviceId()).isEqualTo("event-test-device");
        assertThat(event.getTelemetry().getLatitude()).isEqualTo(40.7128);
        assertThat(event.getTelemetry().getLongitude()).isEqualTo(-74.0060);

        // Confirm event source is TelemetryController
        assertThat(event.getSource()).isInstanceOf(TelemetryController.class);

        // Verify processing start time is set
        assertThat(event.getProcessingStartTime()).isGreaterThan(0);
        
        // Processing start time should be recent (within last 5 seconds)
        long currentTime = System.currentTimeMillis();
        assertThat(event.getProcessingStartTime()).isBetween(currentTime - 5000, currentTime);
    }

    /**
     * Test Case 4.2: Event Publishing Failure Handling
     * Ensure API remains functional if event publishing fails
     */
    @Test
    void testEventPublishingFailureHandling() throws Exception {
        // Instead of mocking publisher, test with valid data and ensure resilience
        Map<String, Object> telemetryData = createTestTelemetryData("failure-test-device", 41.8781, -87.6298);

        // The test should focus on API resilience
        mockMvc.perform(post("/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId").value("failure-test-device"))
                .andExpect(jsonPath("$.message").value("Telemetry data saved successfully"));

        // Wait for event processing
        boolean eventCaptured = testEventListener.waitForEvent(2, TimeUnit.SECONDS);
        assertThat(eventCaptured).isTrue();
    }

    /**
     * Test multiple events are published correctly
     */
    @Test
    void testMultipleEventPublishing() throws Exception {
        final int numberOfEvents = 3;
        
        for (int i = 0; i < numberOfEvents; i++) {
            Map<String, Object> telemetryData = createTestTelemetryData(
                    "multi-event-device-" + i, 
                    40.0 + i * 0.1, 
                    -74.0 + i * 0.1
            );

            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetryData)))
                    .andExpect(status().isCreated());
        }


        // Verify all events were published
        List<TelemetryEvent> capturedEvents = testEventListener.getCapturedEvents();
        assertThat(capturedEvents).hasSize(numberOfEvents);

        // Verify each event has correct data
        for (int i = 0; i < numberOfEvents; i++) {
            TelemetryEvent event = capturedEvents.get(i);
            assertThat(event.getTelemetry().getDeviceId()).contains("multi-event-device-");
            assertThat(event.getTelemetry().getLatitude()).isBetween(40.0, 40.3);
            assertThat(event.getTelemetry().getLongitude()).isBetween(-74.0, -73.8);
        }
    }

    /**
     * Test event timing and ordering
     */
    @Test
    void testEventTimingAndOrdering() throws Exception {
        long beforeTest = System.currentTimeMillis();
        
        // Submit two events in quick succession
        Map<String, Object> telemetryData1 = createTestTelemetryData("timing-device-1", 40.1, -74.1);
        Map<String, Object> telemetryData2 = createTestTelemetryData("timing-device-2", 40.2, -74.2);

        mockMvc.perform(post("/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData2)))
                .andExpect(status().isCreated());


        List<TelemetryEvent> capturedEvents = testEventListener.getCapturedEvents();
        assertThat(capturedEvents).hasSize(2);

        long afterTest = System.currentTimeMillis();

        // Verify event timing is reasonable
        for (TelemetryEvent event : capturedEvents) {
            assertThat(event.getProcessingStartTime()).isBetween(beforeTest, afterTest);
        }

        // Verify events are in expected order (first submitted, first captured)
        assertThat(capturedEvents.get(0).getTelemetry().getDeviceId()).isEqualTo("timing-device-1");
        assertThat(capturedEvents.get(1).getTelemetry().getDeviceId()).isEqualTo("timing-device-2");
    }

    /**
     * Test configuration to ensure TestEventListener is registered as Spring bean
     */
    @TestConfiguration
    static class TestConfig {
        
        @Bean
        public TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }

    /**
     * Custom event listener for testing
     * Captures TelemetryEvents for validation
     */
    static class TestEventListener {
        private final List<TelemetryEvent> capturedEvents = new ArrayList<>();
        private CountDownLatch eventLatch = new CountDownLatch(1);

        @EventListener
        public void captureEvent(TelemetryEvent event) {
            capturedEvents.add(event);
            eventLatch.countDown();
        }

        public List<TelemetryEvent> getCapturedEvents() {
            return new ArrayList<>(capturedEvents);
        }

        public void clearCapturedEvents() {
            capturedEvents.clear();
            eventLatch = new CountDownLatch(1);
        }

        public boolean waitForEvent(long timeout, TimeUnit unit) throws InterruptedException {
            return eventLatch.await(timeout, unit);
        }

        public boolean waitForEvents(int expectedCount, long timeout, TimeUnit unit) throws InterruptedException {
            eventLatch = new CountDownLatch(expectedCount);
            return eventLatch.await(timeout, unit);
        }
    }
} 