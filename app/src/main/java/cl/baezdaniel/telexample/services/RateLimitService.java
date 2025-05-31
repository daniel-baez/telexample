package cl.baezdaniel.telexample.services;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);
    
    // Rate limiting configurations
    private static final int PER_DEVICE_LIMIT = 100; // per minute
    private static final int BURST_LIMIT = 20; // per minute burst protection
    private static final int GLOBAL_LIMIT = 10000; // per minute
    
    private final Cache<String, Bucket> bucketCache;
    private final Bucket globalBucket;
    
    @Value("${rate-limit.enabled:false}")
    private boolean rateLimitEnabled;
    
    @Autowired
    public RateLimitService(Cache<String, Bucket> bucketCache) {
        this.bucketCache = bucketCache;
        this.globalBucket = createGlobalBucket();
    }
    
    /**
     * Checks if the request is allowed for the given key (device ID or IP)
     * @param key The device ID or IP address
     * @param tokens Number of tokens to consume (default 1)
     * @return RateLimitResult containing decision and remaining tokens
     */
    public RateLimitResult checkRateLimit(String key, int tokens) {
        // If rate limiting is disabled, always allow
        if (!rateLimitEnabled) {
            logger.trace("Rate limiting disabled - allowing request for key: {}", key);
            return new RateLimitResult(true, "RATE_LIMITING_DISABLED", Long.MAX_VALUE, 0L);
        }
        
        // Check global limit first
        ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(tokens);
        if (!globalProbe.isConsumed()) {
            logger.warn("🚫 Global rate limit exceeded. Remaining tokens: {}, Retry in: {}ms", 
                globalProbe.getRemainingTokens(), globalProbe.getNanosToWaitForRefill() / 1_000_000);
            return new RateLimitResult(false, "GLOBAL_LIMIT_EXCEEDED", 
                globalProbe.getRemainingTokens(), globalProbe.getNanosToWaitForRefill());
        }
        
        // Check per-device limit
        Bucket deviceBucket = bucketCache.get(key, this::createDeviceBucket);
        ConsumptionProbe deviceProbe = deviceBucket.tryConsumeAndReturnRemaining(tokens);
        
        if (!deviceProbe.isConsumed()) {
            logger.warn("🚫 Device rate limit exceeded for key: {}. Remaining tokens: {}, Retry in: {}ms", 
                key, deviceProbe.getRemainingTokens(), deviceProbe.getNanosToWaitForRefill() / 1_000_000);
            // Return tokens to global bucket since device limit was exceeded
            globalBucket.addTokens(tokens);
            return new RateLimitResult(false, "DEVICE_LIMIT_EXCEEDED", 
                deviceProbe.getRemainingTokens(), deviceProbe.getNanosToWaitForRefill());
        }
        
        logger.debug("✅ Rate limit check passed for key: {}. Device tokens remaining: {}, Global tokens remaining: {}", 
            key, deviceProbe.getRemainingTokens(), globalProbe.getRemainingTokens());
        
        return new RateLimitResult(true, "ALLOWED", 
            deviceProbe.getRemainingTokens(), 0L);
    }
    
    /**
     * Convenience method for single token consumption
     */
    public RateLimitResult checkRateLimit(String key) {
        return checkRateLimit(key, 1);
    }
    
    /**
     * Creates a bucket for per-device rate limiting
     */
    private Bucket createDeviceBucket(String key) {
        BucketConfiguration config = BucketConfiguration.builder()
            // Primary limit: 100 requests per minute
            .addLimit(Bandwidth.simple(PER_DEVICE_LIMIT, Duration.ofMinutes(1)))
            // Burst protection: 20 requests per minute
            .addLimit(Bandwidth.simple(BURST_LIMIT, Duration.ofMinutes(1)))
            .build();
        
        logger.debug("🪣 Created new rate limit bucket for key: {} with limits: {} per minute, {} burst per minute", 
            key, PER_DEVICE_LIMIT, BURST_LIMIT);
        
        return Bucket.builder().addLimit(config.getBandwidths()[0]).addLimit(config.getBandwidths()[1]).build();
    }
    
    /**
     * Creates the global rate limiting bucket
     */
    private Bucket createGlobalBucket() {
        logger.info("🌍 Creating global rate limit bucket with {} requests per minute", GLOBAL_LIMIT);
        return Bucket.builder()
            .addLimit(Bandwidth.simple(GLOBAL_LIMIT, Duration.ofMinutes(1)))
            .build();
    }
    
    /**
     * Gets current bucket statistics for monitoring
     */
    public BucketStats getBucketStats(String key) {
        Bucket deviceBucket = bucketCache.getIfPresent(key);
        long deviceTokens = deviceBucket != null ? deviceBucket.getAvailableTokens() : PER_DEVICE_LIMIT;
        long globalTokens = globalBucket.getAvailableTokens();
        
        return new BucketStats(key, deviceTokens, globalTokens, bucketCache.estimatedSize());
    }
    
    /**
     * Clears rate limiting data for a specific key (useful for testing)
     */
    public void clearRateLimit(String key) {
        bucketCache.invalidate(key);
        logger.debug("🧹 Cleared rate limit data for key: {}", key);
    }
    
    /**
     * Result of rate limit check
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String reason;
        private final long remainingTokens;
        private final long retryAfterNanos;
        
        public RateLimitResult(boolean allowed, String reason, long remainingTokens, long retryAfterNanos) {
            this.allowed = allowed;
            this.reason = reason;
            this.remainingTokens = remainingTokens;
            this.retryAfterNanos = retryAfterNanos;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public long getRemainingTokens() { return remainingTokens; }
        public long getRetryAfterNanos() { return retryAfterNanos; }
        public long getRetryAfterSeconds() { return retryAfterNanos / 1_000_000_000; }
    }
    
    /**
     * Bucket statistics for monitoring
     */
    public static class BucketStats {
        private final String key;
        private final long deviceTokens;
        private final long globalTokens;
        private final long totalBuckets;
        
        public BucketStats(String key, long deviceTokens, long globalTokens, long totalBuckets) {
            this.key = key;
            this.deviceTokens = deviceTokens;
            this.globalTokens = globalTokens;
            this.totalBuckets = totalBuckets;
        }
        
        public String getKey() { return key; }
        public long getDeviceTokens() { return deviceTokens; }
        public long getGlobalTokens() { return globalTokens; }
        public long getTotalBuckets() { return totalBuckets; }
    }
} 