package cl.baezdaniel.telexample.controllers;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import cl.baezdaniel.telexample.services.TelemetryQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@Validated
public class TelemetryController {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryController.class);

    @Autowired
    private TelemetryRepository telemetryRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private TelemetryQueueService queueService;

    @PostMapping("/telemetry")
    public ResponseEntity<Map<String, Object>> createTelemetry(@Valid @RequestBody Telemetry telemetry) {
        // Check if queue-based processing is enabled
        if (queueService.isEnabled()) {
            return createTelemetryQueued(telemetry);
        } else {
            return createTelemetrySync(telemetry);
        }
    }
    
    /**
     * High-performance queue-based telemetry processing
     * Returns immediately after enqueueing (< 5ms response time)
     */
    private ResponseEntity<Map<String, Object>> createTelemetryQueued(@Valid Telemetry telemetry) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        
        logger.debug("🚀 [QUEUE] Processing telemetry for device: {} (requestId: {})", 
                    telemetry.getDeviceId(), requestId);
        
        // Create queue item as HashMap
        Map<String, Object> queueItem = new HashMap<>();
        queueItem.put(TelemetryQueueService.KEY_DEVICE_ID, telemetry.getDeviceId());
        queueItem.put(TelemetryQueueService.KEY_LATITUDE, telemetry.getLatitude());
        queueItem.put(TelemetryQueueService.KEY_LONGITUDE, telemetry.getLongitude());
        queueItem.put(TelemetryQueueService.KEY_TIMESTAMP, telemetry.getTimestamp());
        queueItem.put(TelemetryQueueService.KEY_REQUEST_ID, requestId);
        queueItem.put(TelemetryQueueService.KEY_QUEUED_AT, LocalDateTime.now());
        
        // Attempt to enqueue
        boolean enqueued = queueService.offer(queueItem);
        
        if (!enqueued) {
            // Queue is full - fallback to synchronous processing
            logger.warn("⚠️ Queue overflow for device {} - falling back to sync processing", 
                       telemetry.getDeviceId());
            return createTelemetrySync(telemetry);
        }
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        // Return immediately with 202 Accepted
        Map<String, Object> response = new HashMap<>();
        response.put("status", "queued");
        response.put("requestId", requestId);
        response.put("deviceId", telemetry.getDeviceId());
        response.put("queueSize", queueService.size());
        response.put("message", "Telemetry data queued for processing");
        
        logger.info("✅ [QUEUE] Telemetry queued for device {} in {}ms (requestId: {}, queueSize: {})",
                   telemetry.getDeviceId(), responseTime, requestId, queueService.size());
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("X-Request-ID", requestId)
                .body(response);
    }
    
    /**
     * Traditional synchronous telemetry processing (backward compatibility)
     */
    private ResponseEntity<Map<String, Object>> createTelemetrySync(@Valid Telemetry telemetry) {
        long startTime = System.currentTimeMillis();
        
        logger.debug("📝 [SYNC] Processing telemetry for device: {}", telemetry.getDeviceId());
        
        Telemetry savedTelemetry = telemetryRepository.save(telemetry);
        
        // 🚀 Publish event for async processing
        TelemetryEvent event = new TelemetryEvent(this, savedTelemetry);
        eventPublisher.publishEvent(event);
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedTelemetry.getId());
        response.put("deviceId", savedTelemetry.getDeviceId());
        response.put("message", "Telemetry data saved successfully");
        
        logger.info("✅ [SYNC] Telemetry saved for device {} in {}ms (id: {})",
                   telemetry.getDeviceId(), responseTime, savedTelemetry.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<Telemetry> getLatestTelemetry(@PathVariable String deviceId) {
        logger.info("Getting latest telemetry for device: {}", deviceId);
        
        Optional<Telemetry> latestTelemetry = telemetryRepository.findLatestByDeviceId(deviceId);
        
        if (latestTelemetry.isPresent()) {
            return ResponseEntity.ok(latestTelemetry.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Queue status endpoint for monitoring
     */
    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (queueService.isEnabled()) {
            TelemetryQueueService.QueueStats stats = queueService.getStats();
            status.put("enabled", true);
            status.put("currentSize", stats.getCurrentSize());
            status.put("capacity", stats.getCapacity());
            status.put("utilization", stats.getUtilizationPercent());
            status.put("totalEnqueued", stats.getTotalEnqueued());
            status.put("totalProcessed", stats.getTotalProcessed());
            status.put("totalOverflow", stats.getTotalOverflow());
            status.put("workerCount", stats.getWorkerCount());
            status.put("running", stats.isRunning());
        } else {
            status.put("enabled", false);
            status.put("mode", "synchronous");
        }
        
        return ResponseEntity.ok(status);
    }
} 