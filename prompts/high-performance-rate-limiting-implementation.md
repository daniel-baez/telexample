# High-Performance Rate Limiting Implementation for Telemetry API

## Context
You are working with a high-throughput Spring Boot telemetry service that processes 2000+ events/second from IoT devices. The current system has:

- **TelemetryController** - REST API with `/telemetry` POST endpoint and device query endpoints
- **Async Event Processing** - Event-driven architecture with configurable thread pools
- **H2 Database** - High-performance in-memory database for concurrent access
- **High Throughput** - Handles 2092 events/second with excellent performance
- **Comprehensive Test Suite** - Integration, performance, and end-to-end tests

The system needs rate limiting to prevent abuse, ensure fair usage, and protect against traffic spikes while maintaining its high-performance characteristics.

## Requirements

### Core Architecture
Implement a multi-layered rate limiting system that:
1. **Preserves High Performance** - Minimal latency impact on 2000+ req/sec throughput
2. **Multiple Rate Limit Types** - Global, per-IP, and per-device rate limiting
3. **In-Memory Storage** - Use Caffeine cache for fast, local rate limit tracking
4. **Flexible Configuration** - Configurable limits via application properties
5. **Proper HTTP Responses** - Return 429 Too Many Requests with detailed error info
6. **Non-Blocking** - Rate limiting checks must be extremely fast (< 1ms)

### Rate Limiting Strategy
```
HTTP Request → Rate Limit Filter → Device Rate Check → Controller → Database
      ↓               ↓                    ↓              ↓           ↓
  IP Check     Global Check       Bucket4j Token    Async Event   Response
      ↓               ↓             Consumption      Processing       ↓
 429 if limited  429 if limited        ↓                ↓         201 Created
                                 Continue or 429    Background     or 429
```

## Implementation Specifications

### 1. Dependencies Addition
**File**: `app/build.gradle`

**Add to dependencies block**:
```gradle
// Rate limiting with Bucket4j
implementation 'com.bucket4j:bucket4j-core:8.7.0'
implementation 'com.bucket4j:bucket4j-caffeine:8.7.0'

// Caffeine cache for in-memory storage
implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
```

### 2. Rate Limiting Configuration
**Location**: `app/src/main/java/cl/baezdaniel/telexample/config/RateLimitConfig.java`

**Requirements**:
- `@Configuration` class
- Create `CaffeineProxyManager<String>` bean for Bucket4j
- Configure Caffeine cache with:
  - Maximum size: 100,000 entries
  - Expire after access: 10 minutes
  - Optimized for high-frequency access

### 3. Rate Limiting Service
**Location**: `app/src/main/java/cl/baezdaniel/telexample/services/RateLimitService.java`

**Requirements**:
- `@Service` component
- Inject `CaffeineProxyManager<String>`
- Implement three rate limiting methods:

#### a) Device Rate Limiting
- Method: `allowTelemetryRequest(String deviceId): boolean`
- Limit: 100 requests per minute per device
- Bucket key pattern: "device:{deviceId}"
- Logging: Warn with 🚫 emoji when limit exceeded

#### b) IP Address Rate Limiting  
- Method: `allowIpRequest(String ipAddress): boolean`
- Limit: 200 requests per minute per IP
- Bucket key pattern: "ip:{ipAddress}"
- Handle X-Forwarded-For and X-Real-IP headers

#### c) Global Rate Limiting
- Method: `allowGlobalRequest(): boolean`
- Limit: 500 requests per second globally
- Bucket key: "global"
- Protect against traffic spikes

#### Bucket Configuration
- Use `Bandwidth.classic()` with `Refill.intervally()`
- Create buckets lazily using supplier pattern
- Thread-safe bucket access and token consumption

### 4. Rate Limiting Filter
**Location**: `app/src/main/java/cl/baezdaniel/telexample/filters/RateLimitFilter.java`

**Requirements**:
- Extend `OncePerRequestFilter`
- `@Component` for automatic registration
- Inject `RateLimitService` and `ObjectMapper`
- Filter logic:

#### Request Filtering Logic
1. **Scope**: Only apply to `POST /telemetry` requests
2. **Order**: Global check → IP check → Continue to controller
3. **IP Extraction**: Handle proxy headers (X-Forwarded-For, X-Real-IP)
4. **Error Response**: Return structured JSON with 429 status

#### Error Response Format
```json
{
  "error": "Rate limit exceeded",
  "message": "Specific limit type message",
  "limitType": "GLOBAL_LIMIT|IP_LIMIT|DEVICE_LIMIT",
  "timestamp": 1635789012345
}
```

### 5. Controller Integration
**File**: `app/src/main/java/cl/baezdaniel/telexample/controllers/TelemetryController.java`

**Requirements**:
- Inject `RateLimitService`
- Add device-specific rate limiting to `createTelemetry` method
- Check device rate limit after request body parsing
- Return 429 with device-specific error message if exceeded
- Maintain existing API contract for successful requests

### 6. Configuration Properties
**File**: `app/src/main/resources/application.properties`

**Add**:
```properties
# Rate Limiting Configuration
# Adjust these values based on your expected traffic patterns and capacity

# Device-specific rate limiting (requests per minute)
rate-limit.telemetry.device.requests-per-minute=100

# IP-based rate limiting (requests per minute)  
rate-limit.ip.requests-per-minute=200

# Global rate limiting (requests per second)
rate-limit.global.requests-per-second=500

# Cache configuration
rate-limit.cache.max-size=100000
rate-limit.cache.expire-after-access-minutes=10
```

### 7. Test Configuration Properties
**File**: `app/src/test/resources/application-test.properties`

**Add**:
```properties
# Reduced rate limits for testing
rate-limit.telemetry.device.requests-per-minute=5
rate-limit.ip.requests-per-minute=10
rate-limit.global.requests-per-second=50
```

## Testing Specifications

### 1. Rate Limiting Unit Tests
**Location**: `app/src/test/java/cl/baezdaniel/telexample/ratelimit/RateLimitServiceTest.java`

**Test Cases**:
- `testDeviceRateLimitingSuccess()` - Verify device limits work correctly
- `testDeviceRateLimitingExceeded()` - Test device limit enforcement
- `testIpRateLimitingSuccess()` - Verify IP limits work correctly
- `testIpRateLimitingExceeded()` - Test IP limit enforcement
- `testGlobalRateLimitingSuccess()` - Verify global limits work correctly
- `testGlobalRateLimitingExceeded()` - Test global limit enforcement
- `testConcurrentAccess()` - Verify thread-safety under load

### 2. Integration Tests
**Location**: `app/src/test/java/cl/baezdaniel/telexample/ratelimit/RateLimitIntegrationTest.java`

**Test Cases**:
- `testDeviceRateLimitEndToEnd()` - Full HTTP request rate limiting
- `testIpRateLimitEndToEnd()` - IP-based rate limiting via MockMvc
- `testSuccessfulRequestsNotAffected()` - Verify normal flow unimpacted
- `testRateLimitErrorResponses()` - Validate 429 response format
- `testMultipleDeviceIsolation()` - Ensure device limits are isolated

### 3. Performance Tests
**Location**: `app/src/test/java/cl/baezdaniel/telexample/ratelimit/RateLimitPerformanceTest.java`

**Test Cases**:
- `testRateLimitingPerformanceImpact()` - Measure latency overhead (< 1ms)
- `testHighThroughputWithRateLimiting()` - Verify 1000+ req/sec handling
- `testCachePerformanceUnderLoad()` - Caffeine cache efficiency
- `testMemoryUsageStability()` - Memory consumption patterns

## Technical Specifications

### Performance Requirements
- **Latency Impact**: Rate limiting checks must add < 1ms overhead
- **Throughput Preservation**: System must maintain 2000+ req/sec capability
- **Memory Efficiency**: Cache should use < 50MB for 100K entries
- **Thread Safety**: All rate limiting operations must be thread-safe

### Logging Requirements
- Use SLF4J logger with appropriate levels
- Rate limit violations: WARN with 🚫 emoji
- Include device ID, IP address, and limit type in logs
- Performance metrics: Log cache hit rates and response times

### Error Handling
- Graceful degradation if cache fails (allow requests through)
- Proper exception handling in filter chain
- Detailed error messages for different limit types
- No impact on async event processing pipeline

### Bucket4j Configuration Details
- **Token Bucket Algorithm**: Classic token bucket with interval refill
- **Refill Strategy**: Smooth refill over time intervals
- **Overflow Handling**: Reject requests when bucket is empty
- **Precision**: Second-level precision for high-frequency requests

## Integration Considerations

### Filter Registration Order
- Rate limiting filter should run early in filter chain
- Must run before authentication/authorization filters
- Should not interfere with Spring Security if added later

### Database Impact
- Rate limiting should not impact database performance
- All rate limit data stored in memory (Caffeine cache)
- No database queries for rate limit checks

### Monitoring and Observability
- Log rate limit violations with device/IP identification
- Track cache performance metrics
- Monitor rate limit effectiveness and adjustment needs

## Expected Behavior

### Normal Operation
1. **Under Limits**: Requests process normally with minimal latency
2. **Cache Efficiency**: High cache hit rates, low memory usage
3. **Thread Safety**: No contention or race conditions under load

### Rate Limit Scenarios
1. **Device Limit Exceeded**: 429 response with device-specific message
2. **IP Limit Exceeded**: 429 response with IP-specific message  
3. **Global Limit Exceeded**: 429 response with global limit message

### Example Log Output
```
INFO  Processing telemetry for device: device123
WARN  🚫 Rate limit exceeded for device: device456
WARN  🚫 Rate limit exceeded for IP: 192.168.1.100
WARN  🚫 Global rate limit exceeded
```

### Example 429 Response
```json
{
  "error": "Rate limit exceeded",
  "message": "Device rate limit exceeded",
  "deviceId": "device123",
  "limitType": "DEVICE_LIMIT",
  "timestamp": 1635789012345
}
```

## Success Criteria
- ✅ Rate limiting adds < 1ms latency overhead
- ✅ System maintains 2000+ req/sec throughput capacity
- ✅ Device, IP, and global limits work independently
- ✅ Proper 429 HTTP responses with detailed error information
- ✅ Thread-safe operation under high concurrency
- ✅ Memory-efficient caching with automatic cleanup
- ✅ Comprehensive test coverage for all rate limit scenarios
- ✅ Existing functionality and performance remain unaffected
- ✅ Configurable limits via application properties
- ✅ Graceful degradation and error handling

## Deliverables
1. All rate limiting source files as specified
2. Updated build.gradle with dependencies
3. Configuration properties for rate limits
4. Comprehensive test suite covering all scenarios
5. Performance validation ensuring minimal impact
6. Documentation of rate limiting behavior and configuration
7. Integration with existing telemetry processing pipeline 