# Event-Driven Telemetry Processing System Implementation

## Context
You are working with an existing Spring Boot telemetry service that receives GPS location data from IoT devices. The current system has:

- **TelemetryController** - REST API for receiving telemetry data (`POST /telemetry`) and retrieving latest data (`GET /devices/{deviceId}/telemetry/latest`)
- **Telemetry Entity** - JPA entity with fields: id, deviceId, latitude, longitude, timestamp
- **TelemetryRepository** - JPA repository with SQLite database
- **Existing Test Suite** - Comprehensive integration tests

The system currently saves telemetry data synchronously and returns immediately. We need to add an event-driven architecture for async processing.

## Requirements

### Core Architecture
Implement an event-driven system that:
1. **Publishes events** after successfully saving telemetry data
2. **Processes events asynchronously** using multiple parallel processors
3. **Configurable thread pool** for vertical scalability tuning
4. **Self-contained** - no external message brokers or dependencies
5. **Non-blocking** - API response time remains fast

### Event Flow
```
Telemetry POST → Save to DB → Publish TelemetryEvent → Multiple Async Processors
                    ↓                                           ↓
            Return 201 Created                      [Configurable Thread Pool]
                                                           ↓
                                            [Anomaly Detection] [Statistics] [Alerts] [Aggregation]
```

## Implementation Specifications

### 1. TelemetryEvent Class
**Location**: `app/src/main/java/cl/baezdaniel/telexample/events/TelemetryEvent.java`

**Requirements**:
- Extend `ApplicationEvent`
- Include the saved `Telemetry` object
- Track processing start time for metrics
- Immutable design with proper getters

### 2. AsyncConfig Class
**Location**: `app/src/main/java/cl/baezdaniel/telexample/config/AsyncConfig.java`

**Requirements**:
- `@Configuration` and `@EnableAsync`
- Create bean named `telemetryTaskExecutor`
- Use `ThreadPoolTaskExecutor` with configurable properties:
  - `telemetry.processing.core-pool-size` (default: 4)
  - `telemetry.processing.max-pool-size` (default: 8) 
  - `telemetry.processing.queue-capacity` (default: 100)
- Thread naming prefix: "TelemetryProcessor-"
- Graceful shutdown configuration

### 3. TelemetryProcessors Class
**Location**: `app/src/main/java/cl/baezdaniel/telexample/processors/TelemetryProcessors.java`

**Requirements**:
- `@Component` with multiple `@EventListener` methods
- Each method annotated with `@Async("telemetryTaskExecutor")`
- Implement four processors:

#### a) Anomaly Detection Processor
- Method: `detectAnomalies(TelemetryEvent event)`
- Logic: Check for invalid coordinates, extreme values, rapid movement patterns
- Logging: Use 🔍 emoji, include thread name and device ID
- Error handling: Catch and log exceptions without failing other processors

#### b) Statistics Processor  
- Method: `updateStatistics(TelemetryEvent event)`
- Logic: Calculate processing delays, update device metrics, aggregate data points
- Logging: Use 📊 emoji, include processing time metrics
- Simulate: Device activity tracking, geographic coverage analysis

#### c) Alert System Processor
- Method: `processAlerts(TelemetryEvent event)`  
- Logic: Geofencing checks, restricted area detection, notification triggers
- Logging: Use 🔔 emoji, warn on alert conditions with 🚨
- Example: Alert when device enters/exits predefined geographic boundaries

#### d) Data Aggregation Processor
- Method: `aggregateData(TelemetryEvent event)`
- Logic: Map-reduce style data processing, regional summaries, pattern analysis
- Logging: Use 🗺️ emoji, include coordinate information
- Simulate: Hourly rollups, density maps, movement patterns

### 4. Controller Updates
**File**: `app/src/main/java/cl/baezdaniel/telexample/controllers/TelemetryController.java`

**Requirements**:
- Inject `ApplicationEventPublisher` 
- In `createTelemetry` method, after saving to database:
  - Create new `TelemetryEvent(this, savedTelemetry)`
  - Publish event using `eventPublisher.publishEvent(event)`
  - Add logging: "📡 Published telemetry event for device: {deviceId}"
- Maintain existing API contract and response format

### 5. Configuration Properties
**File**: `app/src/main/resources/application.properties`

**Add**:
```properties
# Telemetry Event Processing Configuration
# Configure the async thread pool for telemetry event processing
# Adjust these values based on your hardware and load requirements

# Core number of threads to keep in the pool
telemetry.processing.core-pool-size=4

# Maximum number of threads in the pool  
telemetry.processing.max-pool-size=8

# Queue capacity for pending tasks
telemetry.processing.queue-capacity=100
```

## Technical Specifications

### Logging Requirements
- Use SLF4J logger in all new classes
- Include thread names in async processor logs: `Thread.currentThread().getName()`
- Use emojis for visual distinction: 🔍 🚨 📊 🔔 🗺️ 📡 📈 📍
- Log at appropriate levels: INFO for normal flow, WARN for anomalies, ERROR for exceptions

### Error Handling
- Each async processor must handle its own exceptions
- Log errors without disrupting other processors
- Include device ID and error details in error logs
- No exceptions should bubble up to break the event processing

### Performance Considerations
- Simulate realistic processing times with `Thread.sleep()`:
  - Anomaly detection: ~100ms
  - Statistics: ~50ms  
  - Alerts: ~75ms
  - Aggregation: ~25ms
- Track processing delays from event creation time
- Log processing metrics for performance monitoring

### Testing Requirements
- Ensure existing tests continue to pass
- API response time should remain fast (< 50ms for simple cases)
- Verify async processing occurs without blocking the main thread
- Test thread pool configuration loading from properties

## Expected Behavior

### When telemetry is posted:
1. **Immediate response** - API returns 201 Created with saved telemetry ID
2. **Async processing** - Four processors run in parallel on separate threads  
3. **Rich logging** - Console shows processing activity with thread names and emojis
4. **Configurable scaling** - Thread pool size adjustable via application properties
5. **Fault tolerance** - Individual processor failures don't affect others

### Example log output:
```
INFO  Creating telemetry for device: device123
INFO  📡 Published telemetry event for device: device123  
INFO  🔍 [Thread: TelemetryProcessor-1] Processing anomaly detection for device: device123
INFO  📊 [Thread: TelemetryProcessor-2] Processing statistics for device: device123
INFO  🔔 [Thread: TelemetryProcessor-3] Processing alerts for device: device123
INFO  🗺️ [Thread: TelemetryProcessor-4] Processing aggregation for device: device123
```

## Deliverables
1. All source files as specified above
2. Updated configuration properties
3. Working integration with existing codebase
4. Verification that existing tests pass
5. Clean, documented code with proper error handling

## Success Criteria
- ✅ Telemetry API remains fast and responsive
- ✅ Events are processed asynchronously on configurable thread pool
- ✅ Multiple processors work in parallel without blocking each other
- ✅ Rich logging shows processing activity and thread utilization
- ✅ Thread pool size can be tuned via configuration properties
- ✅ System handles errors gracefully without cascading failures
- ✅ Existing functionality and tests remain unaffected
