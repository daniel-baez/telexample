package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Background worker thread for processing telemetry queue items.
 * 
 * Each worker continuously polls the queue, saves telemetry to database,
 * and publishes events for async processing. Designed for high throughput
 * with proper error handling and graceful shutdown.
 */
public class QueueWorker implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(QueueWorker.class);
    
    private final BlockingQueue<Map<String, Object>> queue;
    private final TelemetryRepository telemetryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Runnable onProcessedCallback;
    
    private volatile boolean running = true;
    private final String workerName;
    
    public QueueWorker(BlockingQueue<Map<String, Object>> queue,
                      TelemetryRepository telemetryRepository,
                      ApplicationEventPublisher eventPublisher,
                      Runnable onProcessedCallback) {
        this.queue = queue;
        this.telemetryRepository = telemetryRepository;
        this.eventPublisher = eventPublisher;
        this.onProcessedCallback = onProcessedCallback;
        this.workerName = Thread.currentThread().getName();
    }
    
    @Override
    public void run() {
        logger.info("🚀 Queue worker {} started - ready to process telemetry", workerName);
        
        try {
            while (running) {
                try {
                    // Block waiting for queue items (with timeout for shutdown responsiveness)
                    Map<String, Object> queueItem = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    
                    if (queueItem != null) {
                        processQueueItem(queueItem);
                    }
                    
                } catch (InterruptedException e) {
                    logger.info("📤 Worker {} interrupted - shutting down", workerName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("❌ Error in worker {} processing queue item: {}", workerName, e.getMessage(), e);
                    // Continue processing other items despite errors
                }
            }
        } finally {
            logger.info("✅ Queue worker {} stopped", workerName);
        }
    }
    
    private void processQueueItem(Map<String, Object> queueItem) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Extract values from HashMap
            String deviceId = (String) queueItem.get(TelemetryQueueService.KEY_DEVICE_ID);
            Double latitude = (Double) queueItem.get(TelemetryQueueService.KEY_LATITUDE);
            Double longitude = (Double) queueItem.get(TelemetryQueueService.KEY_LONGITUDE);
            LocalDateTime timestamp = (LocalDateTime) queueItem.get(TelemetryQueueService.KEY_TIMESTAMP);
            LocalDateTime queuedAt = (LocalDateTime) queueItem.get(TelemetryQueueService.KEY_QUEUED_AT);
            String requestId = (String) queueItem.get(TelemetryQueueService.KEY_REQUEST_ID);
            
            // Calculate queue wait time for monitoring
            long queueWaitMs = java.time.Duration.between(queuedAt, LocalDateTime.now()).toMillis();
            
            logger.debug("📦 [{}] Processing telemetry for device {} (waitTime: {}ms, requestId: {})",
                        workerName, deviceId, queueWaitMs, requestId);
            
            // Convert queue item to entity
            Telemetry telemetry = new Telemetry(deviceId, latitude, longitude, timestamp);
            
            // Save to database
            Telemetry savedTelemetry = telemetryRepository.save(telemetry);
            
            // Publish event for async processing (anomaly detection, alerts, etc.)
            TelemetryEvent event = new TelemetryEvent(this, savedTelemetry);
            eventPublisher.publishEvent(event);
            
            // Update metrics
            if (onProcessedCallback != null) {
                onProcessedCallback.run();
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            logger.debug("✅ [{}] Successfully processed telemetry for device {} (processingTime: {}ms, queueWait: {}ms)",
                        workerName, deviceId, processingTime, queueWaitMs);
            
            // Log performance warning if processing is slow
            if (processingTime > 100) {
                logger.warn("⚠️ [{}] Slow processing detected: {}ms for device {} (requestId: {})",
                           workerName, processingTime, deviceId, requestId);
            }
            
        } catch (Exception e) {
            String deviceId = (String) queueItem.get(TelemetryQueueService.KEY_DEVICE_ID);
            String requestId = (String) queueItem.get(TelemetryQueueService.KEY_REQUEST_ID);
            
            logger.error("❌ [{}] Failed to process telemetry for device {} (requestId: {}): {}",
                        workerName, deviceId, requestId, e.getMessage(), e);
            
            // TODO: Consider implementing dead letter queue for failed items
            // For now, we log the error and continue processing
        }
    }
    
    /**
     * Signal worker to stop processing
     */
    public void stop() {
        logger.info("🛑 Stopping queue worker {}", workerName);
        running = false;
    }
    
    /**
     * Check if worker is currently running
     */
    public boolean isRunning() {
        return running;
    }
} 