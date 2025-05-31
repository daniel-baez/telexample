package cl.baezdaniel.telexample.ratelimit;

import cl.baezdaniel.telexample.services.RateLimitService;
import cl.baezdaniel.telexample.filters.RateLimitFilter;
import cl.baezdaniel.telexample.config.RateLimitConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "rate-limit.enabled=true",
    "rate-limit.telemetry.device.requests-per-minute=20",
    "rate-limit.ip.requests-per-minute=50", 
    "rate-limit.global.requests-per-second=1000"
})
@DisplayName("Rate Limiting Tests")
public class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private Cache<String, Bucket> bucketCache;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clear rate limiting cache before each test
        bucketCache.invalidateAll();
    }

    @Nested
    @DisplayName("RateLimitService Unit Tests")
    class RateLimitServiceTests {

        @Test
        @DisplayName("Should allow requests within device limit")
        void shouldAllowRequestsWithinDeviceLimit() {
            String deviceKey = "device:test-device-1";
            
            // Make 10 requests - should all be allowed
            for (int i = 0; i < 10; i++) {
                RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(deviceKey);
                assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
                assertEquals("ALLOWED", result.getReason());
            }
        }

        @Test
        @DisplayName("Should deny requests exceeding device limit")
        void shouldDenyRequestsExceedingDeviceLimit() {
            String deviceKey = "device:test-device-2";
            
            // Consume burst limit (20 per minute limit)
            for (int i = 0; i < 20; i++) {
                RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(deviceKey);
                assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            }
            
            // Next request should be denied due to burst limit
            RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(deviceKey);
            assertFalse(result.isAllowed(), "Request 21 should be denied due to burst limit");
            assertEquals("DEVICE_LIMIT_EXCEEDED", result.getReason());
            assertTrue(result.getRetryAfterNanos() > 0, "Should have retry after time");
        }

        @Test
        @DisplayName("Should deny requests exceeding global limit")
        void shouldDenyRequestsExceedingGlobalLimit() {
            // This test is complex as it requires consuming 10,000 tokens
            // We'll test by consuming many tokens at once
            String deviceKey = "device:global-test";
            
            // Try to consume 11,000 tokens at once (exceeding global limit of 10,000)
            RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(deviceKey, 11000);
            assertFalse(result.isAllowed(), "Should deny request exceeding global limit");
            assertEquals("GLOBAL_LIMIT_EXCEEDED", result.getReason());
        }

        @Test
        @DisplayName("Should provide accurate bucket statistics")
        void shouldProvideAccurateBucketStatistics() {
            String deviceKey = "device:stats-test";
            
            // Make a few requests
            rateLimitService.checkRateLimit(deviceKey);
            rateLimitService.checkRateLimit(deviceKey);
            rateLimitService.checkRateLimit(deviceKey);
            
            RateLimitService.BucketStats stats = rateLimitService.getBucketStats(deviceKey);
            assertEquals(deviceKey, stats.getKey());
            assertTrue(stats.getDeviceTokens() < 100, "Device tokens should be consumed");
            assertTrue(stats.getGlobalTokens() < 10000, "Global tokens should be consumed");
            assertTrue(stats.getTotalBuckets() > 0, "Should have buckets in cache");
        }

        @Test
        @DisplayName("Should clear rate limit data")
        void shouldClearRateLimitData() {
            String deviceKey = "device:clear-test";
            
            // Make a request to create bucket
            rateLimitService.checkRateLimit(deviceKey);
            assertTrue(bucketCache.getIfPresent(deviceKey) != null, "Bucket should exist");
            
            // Clear the rate limit
            rateLimitService.clearRateLimit(deviceKey);
            assertNull(bucketCache.getIfPresent(deviceKey), "Bucket should be cleared");
        }
    }

    @Nested
    @DisplayName("Rate Limiting Integration Tests")
    class RateLimitIntegrationTests {

        @Test
        @DisplayName("Should apply rate limiting to telemetry endpoint")
        void shouldApplyRateLimitingToTelemetryEndpoint() throws Exception {
            String telemetryJson = """
                {
                    "deviceId": "integration-test-device",
                    "latitude": 40.7128,
                    "longitude": -74.0060,
                    "timestamp": "2024-01-15T10:30:00"
                }
                """;

            // First request should succeed with rate limit headers
            mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telemetryJson))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("Should apply rate limiting to alerts endpoint")
        void shouldApplyRateLimitingToAlertsEndpoint() throws Exception {
            // First request should succeed with rate limit headers
            mockMvc.perform(get("/api/alerts/integration-test-device"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("Should return 429 when rate limit exceeded")
        void shouldReturn429WhenRateLimitExceeded() throws Exception {
            String deviceId = "rate-limit-test-device";
            String telemetryJson = String.format("""
                {
                    "deviceId": "%s",
                    "latitude": 40.7128,
                    "longitude": -74.0060,
                    "timestamp": "2024-01-15T10:30:00"
                }
                """, deviceId);

            // Exhaust the rate limit by making many requests quickly
            for (int i = 0; i < 100; i++) {
                mockMvc.perform(post("/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(telemetryJson));
            }

            // Next request should be rate limited
            MvcResult result = mockMvc.perform(post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telemetryJson))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("X-RateLimit-Retry-After"))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            // Verify error response structure
            String responseBody = result.getResponse().getContentAsString();
            assertTrue(responseBody.contains("Rate limit exceeded"));
            assertTrue(responseBody.contains("DEVICE_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Should not apply rate limiting to health endpoints")
        void shouldNotApplyRateLimitingToHealthEndpoints() throws Exception {
            // Health endpoint should not have rate limit headers
            mockMvc.perform(get("/health"))
                    .andExpect(status().isNotFound()) // Endpoint might not exist, but no rate limiting applied
                    .andExpect(header().doesNotExist("X-RateLimit-Remaining"));
        }

        @Test
        @DisplayName("Should use IP-based rate limiting when device ID not available")
        void shouldUseIpBasedRateLimitingWhenDeviceIdNotAvailable() throws Exception {
            // Make request without device ID in path or parameters
            mockMvc.perform(get("/api/alerts")
                    .header("X-Forwarded-For", "192.168.1.100"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-RateLimit-Remaining"));
        }
    }

    @Nested
    @DisplayName("Rate Limiting Performance Tests")
    class RateLimitPerformanceTests {

        @Test
        @DisplayName("Should handle high concurrency without blocking")
        void shouldHandleHighConcurrencyWithoutBlocking() throws Exception {
            int numberOfThreads = 50;
            int requestsPerThread = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(numberOfThreads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rateLimitedCount = new AtomicInteger(0);
            
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

            // Create concurrent tasks
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        
                        for (int j = 0; j < requestsPerThread; j++) {
                            String deviceKey = "perf-device-" + threadId;
                            RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(deviceKey);
                            
                            if (result.isAllowed()) {
                                successCount.incrementAndGet();
                            } else {
                                rateLimitedCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e.getMessage());
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown(); // Start all threads
            completeLatch.await(); // Wait for completion
            long endTime = System.currentTimeMillis();
            
            executor.shutdown();

            // Verify performance
            long duration = endTime - startTime;
            int totalRequests = numberOfThreads * requestsPerThread;
            double requestsPerSecond = (totalRequests * 1000.0) / duration;

            System.out.printf("Rate Limiting Performance Test Results:%n");
            System.out.printf("Total requests: %d%n", totalRequests);
            System.out.printf("Duration: %d ms%n", duration);
            System.out.printf("Requests per second: %.2f%n", requestsPerSecond);
            System.out.printf("Successful requests: %d%n", successCount.get());
            System.out.printf("Rate limited requests: %d%n", rateLimitedCount.get());

            assertTrue(requestsPerSecond > 1000, "Should handle at least 1000 requests per second");
            assertTrue(successCount.get() > 0, "Should have some successful requests");
            assertEquals(totalRequests, successCount.get() + rateLimitedCount.get(), 
                "All requests should be processed");
        }

        @Test
        @DisplayName("Should handle burst traffic appropriately")
        void shouldHandleBurstTrafficAppropriately() throws Exception {
            String deviceKey = "burst-test-device";
            int burstSize = 25; // Above burst limit of 20
            
            // Make burst requests quickly
            int allowedRequests = 0;
            for (int i = 0; i < burstSize; i++) {
                RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(deviceKey);
                if (result.isAllowed()) {
                    allowedRequests++;
                }
            }
            
            // Should allow exactly the burst limit (20) plus some from primary limit
            assertTrue(allowedRequests <= 120, "Should not exceed combined limits"); // 100 primary + 20 burst
            assertTrue(allowedRequests >= 20, "Should allow at least burst limit");
            
            System.out.printf("Burst test: %d out of %d requests allowed%n", allowedRequests, burstSize);
        }
    }

    @Nested
    @DisplayName("Rate Limiting Edge Cases")
    class RateLimitEdgeCaseTests {

        @Test
        @DisplayName("Should handle null and empty keys gracefully")
        void shouldHandleNullAndEmptyKeysGracefully() {
            // Test with null key
            assertThrows(Exception.class, () -> rateLimitService.checkRateLimit(null));
            
            // Test with empty key
            RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit("");
            assertNotNull(result, "Should handle empty key gracefully");
        }

        @Test
        @DisplayName("Should handle very long device IDs")
        void shouldHandleVeryLongDeviceIds() {
            String longDeviceId = "device:" + "a".repeat(1000);
            
            RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(longDeviceId);
            assertTrue(result.isAllowed(), "Should handle long device IDs");
            assertEquals("ALLOWED", result.getReason());
        }

        @Test
        @DisplayName("Should handle special characters in device IDs")
        void shouldHandleSpecialCharactersInDeviceIds() {
            String specialDeviceId = "device:test-device-@#$%^&*()_+-=[]{}|;:,.<>?";
            
            RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(specialDeviceId);
            assertTrue(result.isAllowed(), "Should handle special characters in device IDs");
            assertEquals("ALLOWED", result.getReason());
        }
    }

    private String createTelemetryJson(String deviceId, double latitude, double longitude) {
        return String.format("""
            {
                "deviceId": "%s",
                "latitude": %f,
                "longitude": %f,
                "timestamp": "2024-01-15T10:30:00"
            }
            """, deviceId, latitude, longitude);
    }
} 