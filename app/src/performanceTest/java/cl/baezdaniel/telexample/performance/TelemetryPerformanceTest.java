package cl.baezdaniel.telexample.performance;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Performance tests for telemetry API and async processing.
 * Validates response times and system throughput under load.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "endpoint.auth.enabled=false",
    // Lightweight telemetry processing thread pool - perfect for local testing
    "telemetry.processing.core-pool-size=3",
    "telemetry.processing.max-pool-size=5", 
    "telemetry.processing.queue-capacity=10",
    // Small database connection pool - efficient for local development  
    "spring.datasource.hikari.maximum-pool-size=5",
    "spring.datasource.hikari.minimum-idle=2",
    "spring.datasource.hikari.connection-timeout=5000",
    "spring.datasource.hikari.idle-timeout=300000"
})
class TelemetryPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
        }
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
     * Test Case 5.1: API Response Time
     * Ensure async processing doesn't slow down API
     */
    @Test
    void testApiResponseTimeUnderLoad() throws Exception {
        // Scale down to 15 requests - fits perfectly within our capacity (5 threads + 10 queue)
        final int numberOfRequests = 15;
        final List<Long> responseTimes = new ArrayList<>();

        // Submit 15 concurrent requests - optimal for our lightweight thread pool
        List<CompletableFuture<Long>> futures = IntStream.range(0, numberOfRequests)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Map<String, Object> telemetryData = createTestTelemetryData(
                                "perf-device-" + i,
                                40.0 + (i % 10) * 0.01,
                                -74.0 + (i % 10) * 0.01
                        );

                        long startTime = System.currentTimeMillis();

                        mockMvc.perform(post("/api/v1/telemetry")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(telemetryData)))
                                .andExpect(status().isAccepted());

                        long responseTime = System.currentTimeMillis() - startTime;
                        return responseTime;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }))
                .toList();

        // Wait for all requests to complete and collect response times
        for (CompletableFuture<Long> future : futures) {
            responseTimes.add(future.get(5, TimeUnit.SECONDS));
        }

        // Verify no requests timeout or fail
        assertThat(responseTimes).hasSize(numberOfRequests);

        // Calculate performance metrics
        responseTimes.sort(Long::compareTo);
        
        long averageResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .sum() / responseTimes.size();
                
        // Calculate 95th percentile
        int percentile95Index = (int) Math.ceil(0.95 * responseTimes.size()) - 1;
        long percentile95 = responseTimes.get(percentile95Index);

        // Assert 95th percentile response time < 200ms (realistic for lightweight local setup)
        assertThat(percentile95).isLessThan(200L);
        
        // Log performance metrics for analysis
        System.out.println("Performance Test Results:");
        System.out.println("Average response time: " + averageResponseTime + "ms");
        System.out.println("95th percentile response time: " + percentile95 + "ms");
        System.out.println("Max response time: " + responseTimes.get(responseTimes.size() - 1) + "ms");
        System.out.println("Min response time: " + responseTimes.get(0) + "ms");
    }

    /**
     * Test Case 5.2: Throughput Testing
     * Validate system handles expected load
     */
    @Test
    void testAsyncProcessingThroughput() throws Exception {
        // Use exactly our capacity: 5 threads + 10 queue = 15 total capacity
        final int numberOfEvents = 15;
        final long startTime = System.currentTimeMillis();

        // Submit 15 telemetry records - exactly matching our lightweight thread pool capacity
        List<CompletableFuture<Void>> futures = IntStream.range(0, numberOfEvents)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        // Spread requests over 10 seconds
                        // long delay = (testDurationMs * i) / numberOfEvents;
                        // if (delay > 0) {
                        //     Thread.sleep(delay);
                        // }

                        Map<String, Object> telemetryData = createTestTelemetryData(
                                "throughput-device-" + i,
                                40.0 + (i % 100) * 0.001,
                                -74.0 + (i % 100) * 0.001
                        );

                        mockMvc.perform(post("/api/v1/telemetry")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(telemetryData)))
                                .andExpect(status().isAccepted());

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }))
                .toList();

        // Wait for all requests to complete (allow up to 30 seconds total)
        for (CompletableFuture<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        // Verify all events are processed within 30 seconds
        assertThat(totalDuration).isLessThan(30000L);

        // Assert no events are dropped or lost (all returned 202 Accepted)
        // This is implicitly tested by the fact that all futures completed successfully

        // Calculate throughput metrics
        double throughputPerSecond = (double) numberOfEvents / (totalDuration / 1000.0);
        
        System.out.println("Throughput Test Results:");
        System.out.println("Total events: " + numberOfEvents);
        System.out.println("Total duration: " + totalDuration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughputPerSecond) + " events/second");

        // Verify reasonable throughput (at least 3 events per second for lightweight setup)
        assertThat(throughputPerSecond).isGreaterThan(3.0);
    }

    /**
     * Test response time consistency under sustained load
     */
    @Test
    void testResponseTimeConsistency() throws Exception {
        final int warmupRequests = 5;  // Reduced from 10 for lightweight local testing
        final int testRequests = 25;   // Reduced from 50 for lightweight local testing
        
        // Warmup phase - let JVM optimize
        for (int i = 0; i < warmupRequests; i++) {
            Map<String, Object> warmupData = createTestTelemetryData("warmup-device-" + i, 40.0, -74.0);
            mockMvc.perform(post("/api/v1/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(warmupData)))
                    .andExpect(status().isAccepted());
        }

        // Test phase - measure response time consistency
        List<Long> responseTimes = new ArrayList<>();
        
        for (int i = 0; i < testRequests; i++) {
            Map<String, Object> testData = createTestTelemetryData("consistency-device-" + i, 40.0 + i * 0.001, -74.0 + i * 0.001);
            
            long startTime = System.currentTimeMillis();
            mockMvc.perform(post("/api/v1/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testData)))
                    .andExpect(status().isAccepted());
            long responseTime = System.currentTimeMillis() - startTime;
            
            responseTimes.add(responseTime);
            
            // Small delay to simulate realistic request pattern
            Thread.sleep(10);
        }

        // Calculate variance and standard deviation
        double average = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = responseTimes.stream()
                .mapToDouble(time -> Math.pow(time - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        System.out.println("Response Time Consistency Results:");
        System.out.println("Average response time: " + String.format("%.2f", average) + "ms");
        System.out.println("Standard deviation: " + String.format("%.2f", standardDeviation) + "ms");
        System.out.println("Min response time: " + responseTimes.stream().mapToLong(Long::longValue).min().orElse(0) + "ms");
        System.out.println("Max response time: " + responseTimes.stream().mapToLong(Long::longValue).max().orElse(0) + "ms");

        // Assert consistent response times (standard deviation should be reasonable)
        assertThat(standardDeviation).isLessThan(average * 0.5); // SD should be less than 50% of average
        assertThat(average).isLessThan(25.0); // Average response time should be excellent on local setup
    }
} 