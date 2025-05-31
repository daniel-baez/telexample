package cl.baezdaniel.telexample.performance;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.services.TelemetryQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for queue-based telemetry processing.
 * 
 * This test demonstrates the performance improvement from queue-based processing.
 * These tests are designed to run in a separate performance test phase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("performance")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class QueuePerformanceTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TelemetryQueueService queueService;

    @Autowired
    private ApplicationContext applicationContext;
    
    @AfterEach
    void tearDown() throws Exception {
        // Performance tests run in isolation, so we can do immediate shutdown
        if (queueService != null && queueService.isEnabled()) {
            queueService.immediateShutdown();
        }
    }

    @Test
    @DisplayName("🚀 High-Performance Queue Processing Benchmark")
    public void testQueueBasedProcessingPerformance() throws Exception {
        if (!queueService.isEnabled()) {
            fail("❌ Queue processing is DISABLED. Performance tests require queue processing to be enabled.");
        }
        
        System.out.println("\n🚀 PERFORMANCE BENCHMARK: Queue-Based High-Performance Processing");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("✅ Queue service is ENABLED");
        
        // Performance test with higher event count
        int eventCount = 10000;
        long startTime = System.currentTimeMillis();
        
        System.out.printf("📊 Starting benchmark with %d events...\n", eventCount);
        
        // Test queue-based processing
        for (int i = 0; i < eventCount; i++) {
            Telemetry telemetry = createTestTelemetry("perf-device-" + i);
            
            MvcResult result = mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetry)))
                    .andExpect(status().isAccepted()) // 202 for queued processing
                    .andExpect(jsonPath("$.status").value("queued"))
                    .andExpect(jsonPath("$.requestId").exists())
                    .andReturn();
                    
            // Log progress every 1000 events
            if ((i + 1) % 1000 == 0) {
                System.out.printf("   Progress: %d/%d events (%.1f%%)\n", 
                    i + 1, eventCount, (double)(i + 1) / eventCount * 100);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double throughput = (eventCount * 1000.0) / duration;
        
        // Wait for queue processing to complete
        System.out.println("⏳ Waiting for queue processing to complete...");
        Thread.sleep(5000); // More time for larger batch
        TelemetryQueueService.QueueStats stats = queueService.getStats();
        
        System.out.printf("\n🎯 PERFORMANCE BENCHMARK RESULTS:\n");
        System.out.printf("═══════════════════════════════════════\n");
        System.out.printf("   Total events: %,d\n", eventCount);
        System.out.printf("   Duration: %,dms (%.2f seconds)\n", duration, duration / 1000.0);
        System.out.printf("   Throughput: %,.2f events/second\n", throughput);
        System.out.printf("   Avg Response: %.3fms\n", (double) duration / eventCount);
        System.out.printf("   Queue stats: %s\n", stats);
        
        // Performance targets for high-load scenarios
        double targetThroughput = 8000.0; // Higher target for performance tests
        double improvementFactor = throughput / 2300.0; // Baseline comparison
        
        System.out.printf("\n📈 PERFORMANCE ANALYSIS:\n");
        System.out.printf("   Target throughput: %,.0f events/second\n", targetThroughput);
        System.out.printf("   Achieved throughput: %,.2f events/second\n", throughput);
        System.out.printf("   Target met: %s\n", throughput >= targetThroughput ? "✅ YES" : "⚠️ PARTIAL");
        System.out.printf("   Improvement vs baseline: %.1fx faster\n", improvementFactor);
        
        // Performance assertions
        assertTrue(throughput > targetThroughput, 
            String.format("Performance target not met. Expected: >%.0f events/sec, Actual: %.2f events/sec", 
                targetThroughput, throughput));
        assertTrue((double) duration / eventCount < 5, 
            String.format("Average response time too high. Expected: <5ms, Actual: %.3fms", 
                (double) duration / eventCount));
        assertTrue(stats.getTotalEnqueued() > eventCount * 0.95, 
            String.format("Too many events dropped. Expected: >95%% enqueued, Actual: %.1f%%", 
                (double) stats.getTotalEnqueued() / eventCount * 100));
    }
    
    @Test
    @DisplayName("🔄 Queue Capacity and Overflow Handling")
    public void testQueueCapacityAndOverflow() throws Exception {
        System.out.println("\n🔄 CAPACITY TEST: Queue Overflow Handling");
        System.out.println("═══════════════════════════════════════");
        
        // Test with events that might exceed queue capacity
        int eventCount = 6000; // More than default queue capacity
        int successCount = 0;
        int overflowCount = 0;
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < eventCount; i++) {
            Telemetry telemetry = createTestTelemetry("capacity-test-" + i);
            
            MvcResult result = mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetry)))
                    .andReturn();
                    
            if (result.getResponse().getStatus() == 202) {
                successCount++;
            } else {
                overflowCount++;
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        TelemetryQueueService.QueueStats stats = queueService.getStats();
        
        System.out.printf("📊 CAPACITY TEST RESULTS:\n");
        System.out.printf("   Total events submitted: %,d\n", eventCount);
        System.out.printf("   Successfully queued: %,d\n", successCount);
        System.out.printf("   Overflow/rejected: %,d\n", overflowCount);
        System.out.printf("   Success rate: %.1f%%\n", (double) successCount / eventCount * 100);
        System.out.printf("   Queue capacity: %,d\n", stats.getCapacity());
        System.out.printf("   Duration: %,dms\n", duration);
        
        // Verify queue handled capacity gracefully
        assertTrue(successCount > eventCount * 0.8, "At least 80% of events should be queued successfully");
        assertTrue(stats.getCapacity() > 0, "Queue should have configured capacity");
    }
    
    @Test
    @DisplayName("📊 Queue Status and Monitoring")
    public void testQueueStatusAndMonitoring() throws Exception {
        System.out.println("\n📊 MONITORING TEST: Queue Status Endpoints");
        System.out.println("═══════════════════════════════════════");
        
        // Test queue status endpoint
        MvcResult statusResult = mockMvc.perform(get("/queue/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.currentSize").exists())
                .andExpect(jsonPath("$.capacity").exists())
                .andExpect(jsonPath("$.workerCount").exists())
                .andReturn();
        
        String statusJson = statusResult.getResponse().getContentAsString();
        System.out.println("📈 Initial Queue Status: " + statusJson);
        
        // Submit some events
        int testEvents = 100;
        for (int i = 0; i < testEvents; i++) {
            Telemetry telemetry = createTestTelemetry("monitor-test-" + i);
            
            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(telemetry)))
                    .andExpect(status().isAccepted());
        }
        
        // Check updated status
        MvcResult updatedStatusResult = mockMvc.perform(get("/queue/status"))
                .andExpect(status().isOk())
                .andReturn();
        
        String updatedStatusJson = updatedStatusResult.getResponse().getContentAsString();
        System.out.println("📈 Updated Queue Status: " + updatedStatusJson);
        
        TelemetryQueueService.QueueStats stats = queueService.getStats();
        System.out.printf("📊 Final Stats: %s\n", stats);
        
        assertTrue(stats.getTotalEnqueued() >= testEvents, "Should have enqueued test events");
        assertTrue(stats.getWorkerCount() > 0, "Should have active workers");
    }
    
    private Telemetry createTestTelemetry(String deviceId) {
        // Generate realistic coordinates around NYC
        double baseLat = 40.7128;
        double baseLon = -74.0060;
        double randomLat = baseLat + (Math.random() - 0.5) * 0.1;
        double randomLon = baseLon + (Math.random() - 0.5) * 0.1;
        
        return new Telemetry(deviceId, randomLat, randomLon, LocalDateTime.now());
    }
} 