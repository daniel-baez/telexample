package cl.baezdaniel.telexample.dto;

import cl.baezdaniel.telexample.entities.Telemetry;
import java.time.LocalDateTime;

/**
 * Pure data record for telemetry queue items.
 * Flattened structure with all fields at record level for optimal performance.
 */
public record TelemetryQueueItem(
    String deviceId,
    Double latitude,
    Double longitude,
    LocalDateTime timestamp,
    String requestId,
    LocalDateTime queuedAt
) {
    
    /**
     * Create queue item from telemetry entity with automatic queuedAt timestamp
     */
    public static TelemetryQueueItem from(Telemetry telemetry, String requestId) {
        return new TelemetryQueueItem(
            telemetry.getDeviceId(),
            telemetry.getLatitude(),
            telemetry.getLongitude(),
            telemetry.getTimestamp(),
            requestId,
            LocalDateTime.now()
        );
    }
} 