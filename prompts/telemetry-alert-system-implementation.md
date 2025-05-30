# Telemetry Alert System Implementation

## Context
You have a working Spring Boot telemetry service with an event-driven architecture that processes telemetry data through async processors. The system includes:

- **Telemetry Entity** - Core data model with device tracking (SQLite + JPA)
- **Event-Driven Processing** - Async processors for anomaly detection, statistics, alerts, and aggregation
- **REST API** - Telemetry CRUD operations with validation
- **Comprehensive Testing** - Both unit and integration tests with proper async synchronization

Now you need to implement a **production-ready Alert system** suitable for a take-home interview exercise. This feature should demonstrate:
- **Data persistence** with proper entity design
- **Business logic integration** with existing processors
- **Advanced API design** with querying and pagination
- **Test evolution** from log-based to data-based validation
- **Data retention policies** for production scenarios

## Requirements Overview

### Core Features
1. **Alert Entity** - Persistent storage with 3-month retention
2. **Alert Generation** - Integration with existing anomaly and alert processors
3. **Deduplication Logic** - Prevent duplicate alerts for same device/condition
4. **REST API** - Advanced querying with pagination and filtering
5. **Data-Driven Testing** - Replace log assertions with database validation
6. **Retention Management** - Automated cleanup of old alerts

### Technical Constraints
- **Simplicity Focus** - Take-home exercise complexity (not production scale)
- **Existing Architecture** - Leverage current async processing system
- **SQLite Database** - Continue using current setup
- **Spring Boot Best Practices** - Follow established patterns in codebase

## Detailed Implementation Specifications

### 1. Alert Entity and Repository
**File**: `app/src/main/java/cl/baezdaniel/telexample/entities/Alert.java`

#### Alert Entity Requirements
```java
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_device_id", columnList = "deviceId"),
    @Index(name = "idx_alert_type", columnList = "alertType"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class Alert {
    // Core Fields
    private Long id;           // Primary key
    private String deviceId;   // Reference to telemetry device
    private String alertType;  // ANOMALY, GEOFENCE, SPEED, etc.
    private String severity;   // LOW, MEDIUM, HIGH, CRITICAL
    private String message;    // Human-readable description
    private LocalDateTime createdAt;
    
    // Context Fields  
    private Double latitude;   // Location when alert triggered
    private Double longitude;
    private String processorName; // Which processor generated it
    
    // Deduplication Fields
    private String fingerprint; // MD5 hash of (deviceId + alertType + context)
    
    // Business Fields (JSON for flexibility)
    private String metadata;   // Additional context as JSON string
}
```

#### Repository Requirements
**File**: `app/src/main/java/cl/baezdaniel/telexample/repositories/AlertRepository.java`

```java
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    // Basic queries
    List<Alert> findByDeviceId(String deviceId);
    List<Alert> findByDeviceIdOrderByCreatedAtDesc(String deviceId);
    
    // Pagination queries
    Page<Alert> findByDeviceId(String deviceId, Pageable pageable);
    Page<Alert> findByDeviceIdAndAlertType(String deviceId, String alertType, Pageable pageable);
    Page<Alert> findBySeverity(String severity, Pageable pageable);
    
    // Deduplication
    Optional<Alert> findByFingerprint(String fingerprint);
    boolean existsByFingerprint(String fingerprint);
    
    // Retention management
    @Query("DELETE FROM Alert a WHERE a.createdAt < :cutoffDate")
    @Modifying
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Advanced queries for API
    @Query("SELECT a FROM Alert a WHERE a.deviceId = :deviceId AND a.createdAt BETWEEN :start AND :end")
    Page<Alert> findByDeviceIdAndDateRange(
        @Param("deviceId") String deviceId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        Pageable pageable
    );
}
```

### 2. Alert Service Layer
**File**: `app/src/main/java/cl/baezdaniel/telexample/services/AlertService.java`

#### Service Requirements
- **Alert Creation** with deduplication logic
- **Fingerprint Generation** for preventing duplicates
- **Business Rules** for alert severity assignment
- **Retention Management** with scheduled cleanup
- **Query Support** for advanced API filtering

#### Core Methods
```java
@Service
public class AlertService {
    public Alert createAlert(AlertCreationRequest request); // With deduplication
    public Page<Alert> getAlertsForDevice(String deviceId, Pageable pageable);
    public Page<Alert> getAlertsWithFilters(AlertFilterRequest filters, Pageable pageable);
    public void cleanupOldAlerts(); // 3-month retention
    
    // Internal methods
    private String generateFingerprint(String deviceId, String alertType, String context);
    private String determineSeverity(String alertType, String context);
}
```

### 3. Processor Integration
**File**: Update `app/src/main/java/cl/baezdaniel/telexample/processors/TelemetryProcessors.java`

#### Integration Requirements
- **Anomaly Detection Processor** - Create ANOMALY alerts for coordinate violations
- **Alert Processing Processor** - Create GEOFENCE alerts for restricted areas
- **Async Compatibility** - Maintain current async processing model
- **Error Handling** - Graceful degradation if alert creation fails

#### Implementation Pattern
```java
@Autowired
private AlertService alertService;

@EventListener
@Async("telemetryTaskExecutor")
public void detectAnomalies(TelemetryEvent event) {
    // ... existing anomaly detection logic ...
    
    if (anomalyDetected) {
        try {
            AlertCreationRequest alertRequest = AlertCreationRequest.builder()
                .deviceId(deviceId)
                .alertType("ANOMALY")
                .message(String.format("Invalid coordinates detected: lat=%s, lon=%s", lat, lon))
                .latitude(lat)
                .longitude(lon)
                .processorName("AnomalyDetection")
                .metadata(createMetadataJson(telemetry))
                .build();
                
            alertService.createAlert(alertRequest);
            logger.info("🚨 Alert created for device {}: {}", deviceId, alertRequest.getMessage());
        } catch (Exception e) {
            logger.error("Failed to create alert for device {}: {}", deviceId, e.getMessage());
        }
    }
}
```

### 4. REST API Implementation
**File**: `app/src/main/java/cl/baezdaniel/telexample/controllers/AlertController.java`

#### Endpoint Requirements

##### GET /api/alerts/{deviceId}
- **Purpose** - Get all alerts for a specific device
- **Features** - Pagination, sorting, date range filtering
- **Parameters**:
  - `page` (default: 0)
  - `size` (default: 20, max: 100)
  - `sort` (default: createdAt,desc)
  - `alertType` (optional filter)
  - `severity` (optional filter)
  - `startDate` (optional, ISO format)
  - `endDate` (optional, ISO format)

##### GET /api/alerts
- **Purpose** - Get alerts across all devices (admin view)
- **Features** - Same filtering as device-specific endpoint
- **Additional Parameters**:
  - `deviceId` (optional filter)

#### Response Format
```json
{
  "content": [
    {
      "id": 1,
      "deviceId": "device-123",
      "alertType": "ANOMALY",
      "severity": "HIGH",
      "message": "Invalid coordinates detected: lat=95.0, lon=-180.5",
      "latitude": 95.0,
      "longitude": -180.5,
      "processorName": "AnomalyDetection",
      "createdAt": "2024-01-15T10:30:00Z",
      "metadata": "{\"speed\": 0, \"previousLocation\": null}"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "orders": [{"property": "createdAt", "direction": "DESC"}]
    }
  },
  "totalElements": 45,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

### 5. Data Migration and Schema
**File**: `app/src/main/resources/db/migration/V2__create_alerts_table.sql` (if using Flyway)
**Alternative**: JPA will auto-create table, but document expected schema

#### SQL Schema
```sql
CREATE TABLE alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id VARCHAR(255) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    latitude DOUBLE,
    longitude DOUBLE,
    processor_name VARCHAR(100),
    fingerprint VARCHAR(32) UNIQUE,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    
    CONSTRAINT uc_fingerprint UNIQUE (fingerprint)
);

CREATE INDEX idx_alerts_device_id ON alerts(device_id);
CREATE INDEX idx_alerts_alert_type ON alerts(alert_type);
CREATE INDEX idx_alerts_created_at ON alerts(created_at);
CREATE INDEX idx_alerts_severity ON alerts(severity);
```

### 6. Test Migration Strategy
**Objective**: Replace log-based test assertions with database-driven validation

#### Before (Log-Based Testing)
```java
// OLD APPROACH - Looking for log messages
List<String> logMessages = listAppender.list.stream()
    .map(ILoggingEvent::getFormattedMessage)
    .toList();
assertThat(logMessages).anyMatch(msg -> msg.contains("🚨 ANOMALY DETECTED"));
```

#### After (Data-Based Testing)
```java
// NEW APPROACH - Checking actual database records
@Test
void testAnomalyDetectionCreatesAlert() throws Exception {
    // Given
    Telemetry anomalousTelemetry = createTestTelemetry("test-device", 95.0, -180.5);
    TelemetryEvent event = createTestEvent(anomalousTelemetry);
    
    // When
    eventPublisher.publishEvent(event);
    waitForAsyncProcessing();
    
    // Then - Check database instead of logs
    List<Alert> alerts = alertRepository.findByDeviceId("test-device");
    assertThat(alerts).hasSize(1);
    
    Alert createdAlert = alerts.get(0);
    assertThat(createdAlert.getAlertType()).isEqualTo("ANOMALY");
    assertThat(createdAlert.getSeverity()).isEqualTo("HIGH");
    assertThat(createdAlert.getMessage()).contains("Invalid coordinates");
    assertThat(createdAlert.getLatitude()).isEqualTo(95.0);
    assertThat(createdAlert.getLongitude()).isEqualTo(-180.5);
    assertThat(createdAlert.getProcessorName()).isEqualTo("AnomalyDetection");
}
```

#### Updated Test Files
1. **TelemetryProcessorsTest.java** - Replace all log assertions with alert repository queries
2. **AlertServiceTest.java** - New unit tests for service layer
3. **AlertControllerTest.java** - API endpoint tests with MockMvc
4. **AlertRepositoryTest.java** - Repository query tests
5. **TelemetryEventProcessingTest.java** - Update integration tests

### 7. Configuration Updates

#### Application Properties
**File**: `app/src/main/resources/application.properties`
```properties
# Alert system configuration
alert.retention.months=3
alert.cleanup.schedule=0 0 2 * * ? # Daily at 2 AM

# Pagination defaults
alert.api.default-page-size=20
alert.api.max-page-size=100
```

#### Scheduled Tasks
**File**: Update existing config or create `AlertSchedulingConfig.java`
```java
@Configuration
@EnableScheduling
public class AlertSchedulingConfig {
    
    @Autowired
    private AlertService alertService;
    
    @Scheduled(cron = "${alert.cleanup.schedule:0 0 2 * * ?}")
    public void cleanupOldAlerts() {
        alertService.cleanupOldAlerts();
    }
}
```

## Success Criteria

### Functional Requirements ✅
- [ ] Alerts are created automatically when processors detect issues
- [ ] No duplicate alerts for same device/condition combination
- [ ] API returns paginated results with proper filtering
- [ ] Old alerts are automatically cleaned up after 3 months
- [ ] All API endpoints respond within 200ms for normal loads

### Code Quality Requirements ✅
- [ ] Follows existing codebase patterns and conventions
- [ ] Comprehensive test coverage (>90%) for new components
- [ ] Tests validate actual data, not just log messages
- [ ] Proper error handling and graceful degradation
- [ ] Clear documentation and meaningful variable names

### Interview Assessment Criteria ✅
- [ ] **Architecture Understanding** - Proper service layer separation
- [ ] **Database Design** - Efficient indexing and querying
- [ ] **API Design** - RESTful conventions with advanced features
- [ ] **Testing Strategy** - Data-driven test approach
- [ ] **Production Readiness** - Error handling, logging, configuration

## Implementation Order

1. **Alert Entity & Repository** - Core data layer
2. **Alert Service** - Business logic and deduplication
3. **Processor Integration** - Connect to existing async system
4. **REST API** - Controller with advanced querying
5. **Test Migration** - Replace log-based with data-based tests
6. **Configuration & Scheduling** - Retention policies and cleanup
7. **Documentation** - API docs and README updates

## Notes

- **Keep It Simple** - This is a take-home exercise, not a production system
- **Leverage Existing** - Build on the current async processing architecture
- **Demonstrate Skills** - Show understanding of Spring Boot, JPA, testing, and API design
- **Real-World Concerns** - Include deduplication, retention, and error handling
- **Test Evolution** - Moving from logs to data shows advanced testing maturity 