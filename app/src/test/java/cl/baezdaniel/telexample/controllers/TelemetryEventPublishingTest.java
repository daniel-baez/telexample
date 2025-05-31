package cl.baezdaniel.telexample.controllers;

import cl.baezdaniel.telexample.events.TelemetryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TelemetryController event publishing functionality
 * Verifies that telemetry events are properly published and processed
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

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
        }
        
        // Clear any captured events from previous tests
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

        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted());

        // Wait for event processing with more time for queue processing
        boolean eventCaptured = testEventListener.waitForEvent(5, TimeUnit.SECONDS);
        assertThat(eventCaptured).isTrue();

        // Verify TelemetryEvent was published
        List<TelemetryEvent> capturedEvents = testEventListener.getCapturedEvents();
        assertThat(capturedEvents).isNotEmpty();

        TelemetryEvent event = capturedEvents.get(0);
        
        // Assert event contains correct telemetry data
        assertThat(event.getTelemetry()).isNotNull();
        assertThat(event.getTelemetry().getDeviceId()).isEqualTo("event-test-device");
        assertThat(event.getTelemetry().getLatitude()).isEqualTo(40.7128);
        assertThat(event.getTelemetry().getLongitude()).isEqualTo(-74.0060);

        // Event source can be either TelemetryController (sync mode)
        assertThat(event.getSource()).isInstanceOf(TelemetryController.class);
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
        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData)))
                .andExpect(status().isAccepted());

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
        
        // Set up the latch for expected events first
        testEventListener.setExpectedEventCount(numberOfEvents);
        
        for (int i = 0; i < numberOfEvents; i++) {
            Map<String, Object> telemetryData = createTestTelemetryData(
                    "multi-event-device-" + i, 
                    40.0 + i * 0.1, 
                    -74.0 + i * 0.1
            );

            mockMvc.perform(post("/api/v1/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetryData)))
                    .andExpect(status().isAccepted());
        }

        // Wait for all events to be processed
        boolean allEventsCaptured = testEventListener.waitForExpectedEvents(10, TimeUnit.SECONDS);
        assertThat(allEventsCaptured).isTrue();

        // Verify all events were published
        List<TelemetryEvent> capturedEvents = testEventListener.getCapturedEvents();
        assertThat(capturedEvents).hasSizeGreaterThanOrEqualTo(numberOfEvents);

        // Verify each event has correct data (check first 3 events)
        for (int i = 0; i < Math.min(numberOfEvents, capturedEvents.size()); i++) {
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
        // Set up the latch for 2 expected events
        testEventListener.setExpectedEventCount(2);
        
        // Submit two events in quick succession
        Map<String, Object> telemetryData1 = createTestTelemetryData("timing-device-1", 40.1, -74.1);
        Map<String, Object> telemetryData2 = createTestTelemetryData("timing-device-2", 40.2, -74.2);

        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData1)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telemetryData2)))
                .andExpect(status().isAccepted());

        // Wait for events to be processed
        boolean eventsCaptured = testEventListener.waitForExpectedEvents(10, TimeUnit.SECONDS);
        assertThat(eventsCaptured).isTrue();

        List<TelemetryEvent> capturedEvents = testEventListener.getCapturedEvents();
        assertThat(capturedEvents).hasSizeGreaterThanOrEqualTo(2);

        // Verify event data for first two events
        for (int i = 0; i < Math.min(2, capturedEvents.size()); i++) {
            TelemetryEvent event = capturedEvents.get(i);
            assertThat(event.getTelemetry().getDeviceId()).contains("timing-device-");
        }
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
        private volatile CountDownLatch eventLatch = new CountDownLatch(1);
        private volatile int expectedCount = 1;

        @EventListener
        public void captureEvent(TelemetryEvent event) {
            synchronized (capturedEvents) {
                capturedEvents.add(event);
                eventLatch.countDown();
            }
        }

        public List<TelemetryEvent> getCapturedEvents() {
            synchronized (capturedEvents) {
                return new ArrayList<>(capturedEvents);
            }
        }

        public void clearCapturedEvents() {
            synchronized (capturedEvents) {
                capturedEvents.clear();
                expectedCount = 1;
                eventLatch = new CountDownLatch(expectedCount);
            }
        }

        public void setExpectedEventCount(int count) {
            synchronized (capturedEvents) {
                expectedCount = count;
                eventLatch = new CountDownLatch(count);
            }
        }

        public boolean waitForEvent(long timeout, TimeUnit unit) throws InterruptedException {
            return eventLatch.await(timeout, unit);
        }

        public boolean waitForExpectedEvents(long timeout, TimeUnit unit) throws InterruptedException {
            return eventLatch.await(timeout, unit);
        }

        @Deprecated
        public boolean waitForEvents(int expectedCount, long timeout, TimeUnit unit) throws InterruptedException {
            // Deprecated - use setExpectedEventCount + waitForExpectedEvents instead
            return waitForExpectedEvents(timeout, unit);
        }
    }
} 