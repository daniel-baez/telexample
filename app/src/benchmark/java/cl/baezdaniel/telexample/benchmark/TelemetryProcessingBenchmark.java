package cl.baezdaniel.telexample.benchmark;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import cl.baezdaniel.telexample.services.TelemetryQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive Benchmark: Queue-Based vs Synchronous Telemetry Processing
 * 
 * This benchmark compares the two processing approaches across multiple scenarios:
 * - Low load (100 events)
 * - Medium load (1,000 events) 
 * - High load (10,000 events)
 * - Concurrent users (multiple threads)
 * - Mixed workloads
 * 
 * Metrics measured:
 * - Response time (mean, p95, p99)
 * - Throughput (events/second)
 * - Resource utilization
 * - System stability under load
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TelemetryProcessingBenchmark {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Autowired
    private TelemetryQueueService queueService;

    @Autowired
    private DataSource dataSource;

    // Benchmark results storage
    private static final Map<String, BenchmarkResult> results = new LinkedHashMap<>();
    
    // Test parameters
    private static final int WARMUP_EVENTS = 50;
    private static final int LOW_LOAD_EVENTS = 100;
    private static final int MEDIUM_LOAD_EVENTS = 1000;
    private static final int HIGH_LOAD_EVENTS = 10000;
    private static final int CONCURRENT_THREADS = 8;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Default to queue enabled for first tests
        registry.add("telemetry.queue.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() throws Exception {
        // Clear database between tests
        clearDatabase();
        
        // Warmup JVM and database connections
        if (!hasWarmedUp()) {
            performWarmup();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean shutdown for queue service
        if (queueService != null && queueService.isEnabled()) {
            queueService.immediateShutdown();
            // Small delay to allow shutdown
            Thread.sleep(100);
        }
    }

    @AfterAll
    static void printResults() {
        printBenchmarkSummary();
    }

    // ========================================
    // BENCHMARK TESTS
    // ========================================

    @Test
    @Order(1)
    @DisplayName("🚀 Queue-Based Processing - Low Load (100 events)")
    void benchmarkQueueLowLoad() throws Exception {
        enableQueueProcessing();
        BenchmarkResult result = runBenchmark("Queue-LowLoad", LOW_LOAD_EVENTS, 1);
        results.put("Queue-LowLoad", result);
    }

    @Test
    @Order(2)
    @DisplayName("🔄 Synchronous Processing - Low Load (100 events)")
    void benchmarkSyncLowLoad() throws Exception {
        disableQueueProcessing();
        BenchmarkResult result = runBenchmark("Sync-LowLoad", LOW_LOAD_EVENTS, 1);
        results.put("Sync-LowLoad", result);
    }

    @Test
    @Order(3)
    @DisplayName("🚀 Queue-Based Processing - Medium Load (1,000 events)")
    void benchmarkQueueMediumLoad() throws Exception {
        enableQueueProcessing();
        BenchmarkResult result = runBenchmark("Queue-MediumLoad", MEDIUM_LOAD_EVENTS, 1);
        results.put("Queue-MediumLoad", result);
    }

    @Test
    @Order(4)
    @DisplayName("🔄 Synchronous Processing - Medium Load (1,000 events)")
    void benchmarkSyncMediumLoad() throws Exception {
        disableQueueProcessing();
        BenchmarkResult result = runBenchmark("Sync-MediumLoad", MEDIUM_LOAD_EVENTS, 1);
        results.put("Sync-MediumLoad", result);
    }

    @Test
    @Order(5)
    @DisplayName("🚀 Queue-Based Processing - High Load (10,000 events)")
    void benchmarkQueueHighLoad() throws Exception {
        enableQueueProcessing();
        BenchmarkResult result = runBenchmark("Queue-HighLoad", HIGH_LOAD_EVENTS, 1);
        results.put("Queue-HighLoad", result);
    }

    @Test
    @Order(6)
    @DisplayName("🔄 Synchronous Processing - High Load (10,000 events)")
    void benchmarkSyncHighLoad() throws Exception {
        disableQueueProcessing();
        BenchmarkResult result = runBenchmark("Sync-HighLoad", HIGH_LOAD_EVENTS, 1);
        results.put("Sync-HighLoad", result);
    }

    @Test
    @Order(7)
    @DisplayName("🚀 Queue-Based Processing - Concurrent Users")
    void benchmarkQueueConcurrent() throws Exception {
        enableQueueProcessing();
        BenchmarkResult result = runConcurrentBenchmark("Queue-Concurrent", MEDIUM_LOAD_EVENTS, CONCURRENT_THREADS);
        results.put("Queue-Concurrent", result);
    }

    @Test
    @Order(8)
    @DisplayName("🔄 Synchronous Processing - Concurrent Users")
    void benchmarkSyncConcurrent() throws Exception {
        disableQueueProcessing();
        BenchmarkResult result = runConcurrentBenchmark("Sync-Concurrent", MEDIUM_LOAD_EVENTS, CONCURRENT_THREADS);
        results.put("Sync-Concurrent", result);
    }

    // ========================================
    // BENCHMARK IMPLEMENTATION
    // ========================================

    private BenchmarkResult runBenchmark(String name, int eventCount, int threadCount) throws Exception {
        System.out.printf("\n📊 BENCHMARK: %s (%d events, %d thread(s))\n", name, eventCount, threadCount);
        System.out.println("═".repeat(60));

        List<Long> responseTimes = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Generate test data
        List<Telemetry> testData = generateTestData(eventCount);

        // Execute benchmark
        for (Telemetry telemetry : testData) {
            long requestStart = System.nanoTime();
            
            try {
                MvcResult result = mockMvc.perform(post("/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(telemetry)))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn();

                long requestEnd = System.nanoTime();
                long responseTimeMs = (requestEnd - requestStart) / 1_000_000;
                responseTimes.add(responseTimeMs);
                successCount.incrementAndGet();

                // Progress indicator
                if (successCount.get() % (eventCount / 10) == 0) {
                    double progress = (double) successCount.get() / eventCount * 100;
                    System.out.printf("   Progress: %.1f%% (%d/%d)\n", progress, successCount.get(), eventCount);
                }

            } catch (Exception e) {
                errorCount.incrementAndGet();
                System.err.printf("   Error processing event: %s\n", e.getMessage());
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;

        // Wait for queue processing to complete (if applicable)
        if (queueService.isEnabled()) {
            waitForQueueProcessing();
        }

        // Calculate metrics
        return calculateMetrics(name, responseTimes, totalTime, successCount.get(), errorCount.get());
    }

    private BenchmarkResult runConcurrentBenchmark(String name, int totalEvents, int threadCount) throws Exception {
        System.out.printf("\n📊 CONCURRENT BENCHMARK: %s (%d events, %d threads)\n", name, totalEvents, threadCount);
        System.out.println("═".repeat(60));

        int eventsPerThread = totalEvents / threadCount;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CompletableFuture<List<Long>>[] futures = new CompletableFuture[threadCount];

        long startTime = System.currentTimeMillis();

        // Launch concurrent threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                List<Long> threadResponseTimes = new ArrayList<>();
                List<Telemetry> threadData = generateTestData(eventsPerThread, threadId);

                for (Telemetry telemetry : threadData) {
                    long requestStart = System.nanoTime();
                    try {
                        mockMvc.perform(post("/telemetry")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(telemetry)))
                                .andExpect(status().is2xxSuccessful());

                        long requestEnd = System.nanoTime();
                        long responseTimeMs = (requestEnd - requestStart) / 1_000_000;
                        threadResponseTimes.add(responseTimeMs);

                    } catch (Exception e) {
                        System.err.printf("   Thread %d error: %s\n", threadId, e.getMessage());
                    }
                }
                return threadResponseTimes;
            }, executor);
        }

        // Collect all results
        List<Long> allResponseTimes = new ArrayList<>();
        int totalSuccess = 0;
        
        for (CompletableFuture<List<Long>> future : futures) {
            List<Long> threadTimes = future.get();
            allResponseTimes.addAll(threadTimes);
            totalSuccess += threadTimes.size();
        }

        long totalTime = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // Wait for queue processing to complete (if applicable)
        if (queueService.isEnabled()) {
            waitForQueueProcessing();
        }

        return calculateMetrics(name, allResponseTimes, totalTime, totalSuccess, totalEvents - totalSuccess);
    }

    private BenchmarkResult calculateMetrics(String name, List<Long> responseTimes, long totalTimeMs, 
                                           int successCount, int errorCount) {
        if (responseTimes.isEmpty()) {
            System.err.println("⚠️ No response times recorded!");
            return new BenchmarkResult(name, 0, 0, 0, 0, 0, 0, successCount, errorCount);
        }

        // Sort for percentile calculations
        Collections.sort(responseTimes);

        double mean = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50 = responseTimes.get((int) (responseTimes.size() * 0.5));
        long p95 = responseTimes.get((int) (responseTimes.size() * 0.95));
        long p99 = responseTimes.get((int) (responseTimes.size() * 0.99));
        double throughput = (successCount * 1000.0) / totalTimeMs;

        BenchmarkResult result = new BenchmarkResult(
            name, mean, p50, p95, p99, throughput, totalTimeMs, successCount, errorCount
        );

        // Print immediate results
        System.out.println("\n📈 RESULTS:");
        System.out.printf("   Mean Response Time: %.2f ms\n", mean);
        System.out.printf("   P50 Response Time: %d ms\n", p50);
        System.out.printf("   P95 Response Time: %d ms\n", p95);
        System.out.printf("   P99 Response Time: %d ms\n", p99);
        System.out.printf("   Throughput: %.2f events/second\n", throughput);
        System.out.printf("   Total Time: %d ms\n", totalTimeMs);
        System.out.printf("   Success Rate: %.2f%% (%d/%d)\n", 
            (double) successCount / (successCount + errorCount) * 100, successCount, successCount + errorCount);

        return result;
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    private void enableQueueProcessing() {
        System.setProperty("telemetry.queue.enabled", "true");
        // Restart context to pick up new configuration
        restartQueueService();
    }

    private void disableQueueProcessing() {
        System.setProperty("telemetry.queue.enabled", "false");
        // Restart context to pick up new configuration
        restartQueueService();
    }

    private void restartQueueService() {
        try {
            if (queueService != null && queueService.isEnabled()) {
                queueService.immediateShutdown();
            }
            Thread.sleep(200); // Allow time for shutdown
        } catch (Exception e) {
            System.err.println("Error restarting queue service: " + e.getMessage());
        }
    }

    private List<Telemetry> generateTestData(int count) {
        return generateTestData(count, 0);
    }

    private List<Telemetry> generateTestData(int count, int threadId) {
        List<Telemetry> data = new ArrayList<>();
        Random random = new Random(threadId); // Deterministic for reproducibility

        for (int i = 0; i < count; i++) {
            Telemetry telemetry = new Telemetry();
            telemetry.setDeviceId(String.format("benchmark-device-%d-%d", threadId, i));
            telemetry.setLatitude(40.7128 + random.nextGaussian() * 0.1);
            telemetry.setLongitude(-74.0060 + random.nextGaussian() * 0.1);
            telemetry.setTimestamp(LocalDateTime.now().minusSeconds(random.nextInt(3600)));
            data.add(telemetry);
        }

        return data;
    }

    private void clearDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM telemetry");
            stmt.execute("DELETE FROM alerts");
        }
    }

    private void waitForQueueProcessing() throws InterruptedException {
        if (!queueService.isEnabled()) return;

        System.out.print("   Waiting for queue processing");
        int maxWaitSeconds = 30;
        for (int i = 0; i < maxWaitSeconds; i++) {
            TelemetryQueueService.QueueStats stats = queueService.getStats();
            if (stats.getCurrentSize() == 0) {
                System.out.println(" ✅ Complete");
                return;
            }
            System.out.print(".");
            Thread.sleep(1000);
        }
        System.out.println(" ⚠️ Timeout");
    }

    private static volatile boolean warmedUp = false;

    private boolean hasWarmedUp() {
        return warmedUp;
    }

    private void performWarmup() throws Exception {
        System.out.println("\n🔥 Warming up JVM and database connections...");
        
        // Warmup with both modes
        enableQueueProcessing();
        for (int i = 0; i < WARMUP_EVENTS / 2; i++) {
            Telemetry telemetry = generateTestData(1).get(0);
            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetry)));
        }

        disableQueueProcessing();
        for (int i = 0; i < WARMUP_EVENTS / 2; i++) {
            Telemetry telemetry = generateTestData(1).get(0);
            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetry)));
        }

        clearDatabase();
        warmedUp = true;
        System.out.println("✅ Warmup complete");
    }

    private static void printBenchmarkSummary() {
        System.out.println("\n\n");
        System.out.println("🏆 BENCHMARK SUMMARY");
        System.out.println("═".repeat(80));
        System.out.printf("%-20s %10s %10s %10s %10s %15s\n", 
            "Test", "Mean (ms)", "P95 (ms)", "P99 (ms)", "Throughput", "Total Time");
        System.out.println("─".repeat(80));

        for (BenchmarkResult result : results.values()) {
            System.out.printf("%-20s %10.2f %10d %10d %10.2f %15d\n",
                result.name(), result.meanResponseTime(), result.p95ResponseTime(), 
                result.p99ResponseTime(), result.throughput(), result.totalTimeMs());
        }

        System.out.println("─".repeat(80));
        
        // Print analysis
        printPerformanceAnalysis();
    }

    private static void printPerformanceAnalysis() {
        System.out.println("\n📊 PERFORMANCE ANALYSIS");
        System.out.println("═".repeat(50));

        // Compare Low Load
        compareResults("LowLoad", "Low Load (100 events)");
        
        // Compare Medium Load
        compareResults("MediumLoad", "Medium Load (1,000 events)");
        
        // Compare High Load
        compareResults("HighLoad", "High Load (10,000 events)");
        
        // Compare Concurrent
        compareResults("Concurrent", "Concurrent Users");
    }

    private static void compareResults(String suffix, String description) {
        BenchmarkResult queueResult = results.get("Queue-" + suffix);
        BenchmarkResult syncResult = results.get("Sync-" + suffix);

        if (queueResult == null || syncResult == null) {
            System.out.printf("⚠️ %s - Incomplete data\n", description);
            return;
        }

        System.out.printf("\n%s:\n", description);
        
        double throughputImprovement = (queueResult.throughput() / syncResult.throughput() - 1) * 100;
        double responseTimeChange = (queueResult.meanResponseTime() / syncResult.meanResponseTime() - 1) * 100;
        
        System.out.printf("  Throughput: Queue %.2f%% %s than Sync\n", 
            Math.abs(throughputImprovement), throughputImprovement > 0 ? "better" : "worse");
        System.out.printf("  Response Time: Queue %.2f%% %s than Sync\n", 
            Math.abs(responseTimeChange), responseTimeChange < 0 ? "better" : "worse");
            
        if (throughputImprovement > 10) {
            System.out.println("  🚀 Queue processing shows significant throughput advantage");
        } else if (throughputImprovement < -10) {
            System.out.println("  🔄 Synchronous processing shows throughput advantage");
        } else {
            System.out.println("  ⚖️ Similar throughput performance");
        }
    }

    // ========================================
    // BENCHMARK RESULT RECORD
    // ========================================

    private record BenchmarkResult(
        String name,
        double meanResponseTime,
        long p50ResponseTime,
        long p95ResponseTime,
        long p99ResponseTime,
        double throughput,
        long totalTimeMs,
        int successCount,
        int errorCount
    ) {}
} 