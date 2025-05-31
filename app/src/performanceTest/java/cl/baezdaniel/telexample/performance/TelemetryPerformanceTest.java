package cl.baezdaniel.telexample.performance;

import cl.baezdaniel.telexample.services.TelemetryQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
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
 * Advanced performance tests for telemetry API and async processing.
 * Validates response times and system throughput under sustained high load.
 * 
 * These tests are designed to run separately from unit tests to avoid
 * impacting development workflow with long-running performance benchmarks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("performance")
@Transactional
class TelemetryPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TelemetryQueueService queueService;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() throws Exception {
        // Reset database state between tests
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
        }
        
        System.out.println("\n🔧 PERFORMANCE TEST SETUP");
        System.out.println("═══════════════════════════");
        System.out.println("✅ Queue enabled: " + queueService.isEnabled());
        if (queueService.isEnabled()) {
            TelemetryQueueService.QueueStats stats = queueService.getStats();
            System.out.println("📊 Queue stats: " + stats);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // Performance tests run in isolation, so we can do immediate shutdown
        if (queueService != null && queueService.isEnabled()) {
            queueService.immediateShutdown();
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
     * Stress Test: API Response Time Under Heavy Load
     * Tests sustained throughput with 500 concurrent requests
     */
    @Test
    void testApiResponseTimeUnderHeavyLoad() throws Exception {
        System.out.println("\n🔥 STRESS TEST: API Response Time Under Heavy Load");
        System.out.println("═══════════════════════════════════════════════════");
        
        final int numberOfRequests = 500; // Increased for performance testing
        final List<Long> responseTimes = new ArrayList<>();

        System.out.printf("📊 Submitting %d concurrent requests...\n", numberOfRequests);
        
        long testStartTime = System.currentTimeMillis();

        // Submit concurrent requests
        List<CompletableFuture<Long>> futures = IntStream.range(0, numberOfRequests)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Map<String, Object> telemetryData = createTestTelemetryData(
                                "stress-device-" + i,
                                40.0 + (i % 100) * 0.01,
                                -74.0 + (i % 100) * 0.01
                        );

                        long startTime = System.currentTimeMillis();

                        mockMvc.perform(post("/telemetry")
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
            responseTimes.add(future.get(10, TimeUnit.SECONDS));
        }
        
        long totalTestTime = System.currentTimeMillis() - testStartTime;

        // Verify no requests timeout or fail
        assertThat(responseTimes).hasSize(numberOfRequests);

        // Calculate performance metrics
        responseTimes.sort(Long::compareTo);
        
        long averageResponseTime = responseTimes.stream()
                .mapToLong(Long::longValue)
                .sum() / responseTimes.size();
                
        // Calculate percentiles
        int percentile95Index = (int) Math.ceil(0.95 * responseTimes.size()) - 1;
        int percentile99Index = (int) Math.ceil(0.99 * responseTimes.size()) - 1;
        long percentile95 = responseTimes.get(percentile95Index);
        long percentile99 = responseTimes.get(percentile99Index);
        
        double throughput = (numberOfRequests * 1000.0) / totalTestTime;

        System.out.printf("\n📈 STRESS TEST RESULTS:\n");
        System.out.printf("   Total requests: %,d\n", numberOfRequests);
        System.out.printf("   Total time: %,dms\n", totalTestTime);
        System.out.printf("   Throughput: %,.2f requests/second\n", throughput);
        System.out.printf("   Average response time: %dms\n", averageResponseTime);
        System.out.printf("   95th percentile: %dms\n", percentile95);
        System.out.printf("   99th percentile: %dms\n", percentile99);
        System.out.printf("   Max response time: %dms\n", responseTimes.get(responseTimes.size() - 1));
        System.out.printf("   Min response time: %dms\n", responseTimes.get(0));

        // Performance assertions for stress testing
        assertThat(percentile95).isLessThan(50L);  // 95% of requests under 50ms
        assertThat(percentile99).isLessThan(100L); // 99% of requests under 100ms
        assertThat(throughput).isGreaterThan(100.0); // At least 100 requests/second
        
        System.out.println("✅ Stress test passed all performance criteria");
    }

    /**
     * Sustained Load Test: High Throughput Over Extended Period
     * Tests system stability under sustained load
     */
    @Test
    void testSustainedHighThroughput() throws Exception {
        System.out.println("\n⏱️ SUSTAINED LOAD TEST: High Throughput Over Extended Period");
        System.out.println("═══════════════════════════════════════════════════════════");
        
        final int numberOfEvents = 2000; // Higher count for sustained testing
        final int batchSize = 100;
        final long startTime = System.currentTimeMillis();
        
        System.out.printf("📊 Testing sustained load: %d events in batches of %d\n", numberOfEvents, batchSize);

        List<Double> batchThroughputs = new ArrayList<>();
        
        // Submit events in batches to measure sustained performance
        for (int batch = 0; batch < numberOfEvents / batchSize; batch++) {
            long batchStartTime = System.currentTimeMillis();
            final int currentBatch = batch; // Make effectively final for lambda
            
            List<CompletableFuture<Void>> batchFutures = IntStream.range(0, batchSize)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            int eventIndex = currentBatch * batchSize + i;
                            Map<String, Object> telemetryData = createTestTelemetryData(
                                    "sustained-device-" + eventIndex,
                                    40.0 + (eventIndex % 200) * 0.001,
                                    -74.0 + (eventIndex % 200) * 0.001
                            );

                            mockMvc.perform(post("/telemetry")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(telemetryData)))
                                    .andExpect(status().isAccepted());

                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .toList();

            // Wait for batch to complete
            for (CompletableFuture<Void> future : batchFutures) {
                future.get(5, TimeUnit.SECONDS);
            }
            
            long batchDuration = System.currentTimeMillis() - batchStartTime;
            double batchThroughput = (batchSize * 1000.0) / batchDuration;
            batchThroughputs.add(batchThroughput);
            
            System.out.printf("   Batch %2d/%d: %3d events in %4dms (%.1f events/sec)\n", 
                batch + 1, numberOfEvents / batchSize, batchSize, batchDuration, batchThroughput);
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        double overallThroughput = (numberOfEvents * 1000.0) / totalDuration;
        
        // Calculate throughput statistics
        double avgBatchThroughput = batchThroughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double minBatchThroughput = batchThroughputs.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxBatchThroughput = batchThroughputs.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        System.out.printf("\n📊 SUSTAINED LOAD TEST RESULTS:\n");
        System.out.printf("   Total events: %,d\n", numberOfEvents);
        System.out.printf("   Total duration: %,dms (%.1f seconds)\n", totalDuration, totalDuration / 1000.0);
        System.out.printf("   Overall throughput: %,.2f events/second\n", overallThroughput);
        System.out.printf("   Average batch throughput: %,.2f events/second\n", avgBatchThroughput);
        System.out.printf("   Min batch throughput: %,.2f events/second\n", minBatchThroughput);
        System.out.printf("   Max batch throughput: %,.2f events/second\n", maxBatchThroughput);
        System.out.printf("   Throughput consistency: %,.1f%% (min/avg)\n", (minBatchThroughput / avgBatchThroughput) * 100);

        // Verify sustained performance
        assertThat(totalDuration).isLessThan(60000L); // Complete within 60 seconds
        assertThat(overallThroughput).isGreaterThan(50.0); // Maintain >50 events/second overall
        assertThat(minBatchThroughput).isGreaterThan(avgBatchThroughput * 0.7); // No batch drops below 70% of average
        
        System.out.println("✅ Sustained load test passed all performance criteria");
    }

    /**
     * Latency Distribution Test: Response Time Consistency
     * Analyzes response time distribution under normal load
     */
    @Test
    void testResponseTimeDistribution() throws Exception {
        System.out.println("\n📊 LATENCY DISTRIBUTION TEST: Response Time Consistency");
        System.out.println("════════════════════════════════════════════════════════");
        
        final int warmupRequests = 50;  // Increased warmup
        final int testRequests = 200;   // More samples for better distribution analysis
        
        System.out.printf("🔥 Warming up with %d requests...\n", warmupRequests);
        
        // Warmup phase - let JVM optimize
        for (int i = 0; i < warmupRequests; i++) {
            Map<String, Object> telemetryData = createTestTelemetryData(
                    "warmup-device-" + i, 40.0, -74.0
            );

            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetryData)))
                    .andExpect(status().isAccepted());
        }
        
        System.out.printf("📏 Measuring response time distribution with %d requests...\n", testRequests);

        // Measure consistent response times
        List<Long> responseTimes = new ArrayList<>();
        
        for (int i = 0; i < testRequests; i++) {
            Map<String, Object> telemetryData = createTestTelemetryData(
                    "latency-device-" + i, 
                    40.0 + i * 0.001, 
                    -74.0 + i * 0.001
            );

            long startTime = System.currentTimeMillis();
            
            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetryData)))
                    .andExpect(status().isAccepted());
                    
            long responseTime = System.currentTimeMillis() - startTime;
            responseTimes.add(responseTime);
        }

        // Sort for percentile calculations
        responseTimes.sort(Long::compareTo);

        // Calculate statistics
        double mean = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = responseTimes.stream()
                .mapToDouble(time -> Math.pow(time - mean, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = stdDev / mean;
        
        // Calculate percentiles
        long p50 = responseTimes.get((int) (0.50 * responseTimes.size()));
        long p90 = responseTimes.get((int) (0.90 * responseTimes.size()));
        long p95 = responseTimes.get((int) (0.95 * responseTimes.size()));
        long p99 = responseTimes.get((int) (0.99 * responseTimes.size()));

        System.out.printf("\n📈 LATENCY DISTRIBUTION RESULTS:\n");
        System.out.printf("   Sample size: %d requests\n", testRequests);
        System.out.printf("   Mean response time: %.2fms\n", mean);
        System.out.printf("   Standard deviation: %.2fms\n", stdDev);
        System.out.printf("   Coefficient of variation: %.2f\n", coefficientOfVariation);
        System.out.printf("   Min response time: %dms\n", responseTimes.get(0));
        System.out.printf("   50th percentile (median): %dms\n", p50);
        System.out.printf("   90th percentile: %dms\n", p90);
        System.out.printf("   95th percentile: %dms\n", p95);
        System.out.printf("   99th percentile: %dms\n", p99);
        System.out.printf("   Max response time: %dms\n", responseTimes.get(responseTimes.size() - 1));

        // Performance criteria for latency distribution
        assertThat(coefficientOfVariation).isLessThan(0.8); // Reasonable consistency
        assertThat(mean).isLessThan(50.0); // Average response time should be reasonable
        assertThat(p95).isLessThan(100L); // 95% of requests under 100ms
        assertThat(p99).isLessThan(200L); // 99% of requests under 200ms
        
        System.out.println("✅ Latency distribution test passed all performance criteria");
    }

    /**
     * Memory Usage Test: Validate system doesn't leak memory under load
     * Monitors memory usage during sustained operations
     */
    @Test
    void testMemoryUsageUnderLoad() throws Exception {
        System.out.println("\n🧠 MEMORY USAGE TEST: Memory Efficiency Under Load");
        System.out.println("═══════════════════════════════════════════════════");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection and measure baseline
        System.gc();
        Thread.sleep(100);
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.printf("📊 Baseline memory usage: %,.2f MB\n", baselineMemory / 1024.0 / 1024.0);
        
        final int numberOfEvents = 1000;
        List<Long> memorySnapshots = new ArrayList<>();
        
        System.out.printf("🔄 Processing %d events and monitoring memory...\n", numberOfEvents);
        
        for (int i = 0; i < numberOfEvents; i++) {
            Map<String, Object> telemetryData = createTestTelemetryData(
                    "memory-test-device-" + i,
                    40.0 + (i % 50) * 0.01,
                    -74.0 + (i % 50) * 0.01
            );

            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetryData)))
                    .andExpect(status().isAccepted());
                    
            // Take memory snapshot every 100 events
            if (i % 100 == 0) {
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                memorySnapshots.add(currentMemory);
                System.out.printf("   Event %4d: %,.2f MB\n", i, currentMemory / 1024.0 / 1024.0);
            }
        }
        
        // Final memory measurement
        System.gc();
        Thread.sleep(100);
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        
        long memoryIncrease = finalMemory - baselineMemory;
        double memoryIncreasePercent = (double) memoryIncrease / baselineMemory * 100;
        
        System.out.printf("\n📊 MEMORY USAGE RESULTS:\n");
        System.out.printf("   Baseline memory: %,.2f MB\n", baselineMemory / 1024.0 / 1024.0);
        System.out.printf("   Final memory: %,.2f MB\n", finalMemory / 1024.0 / 1024.0);
        System.out.printf("   Memory increase: %,.2f MB (%.1f%%)\n", 
            memoryIncrease / 1024.0 / 1024.0, memoryIncreasePercent);
        System.out.printf("   Max heap size: %,.2f MB\n", runtime.maxMemory() / 1024.0 / 1024.0);
        System.out.printf("   Heap utilization: %.1f%%\n", 
            (double) finalMemory / runtime.maxMemory() * 100);
        
        // Memory efficiency criteria
        assertThat(memoryIncreasePercent).isLessThan(50.0); // Memory shouldn't increase by more than 50%
        assertThat(finalMemory).isLessThan((long)(runtime.maxMemory() * 0.8)); // Don't use more than 80% of heap
        
        System.out.println("✅ Memory usage test passed all efficiency criteria");
    }
} 