package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.dto.TelemetryQueueItem;
import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
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
    
    private final BlockingQueue<TelemetryQueueItem> queue;
    private final TelemetryRepository telemetryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int workerId;
    private volatile boolean shutdown = false;
    
    public QueueWorker(int workerId,
            BlockingQueue<TelemetryQueueItem> queue,
            TelemetryRepository telemetryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.workerId = workerId;
        this.queue = queue;
        this.telemetryRepository = telemetryRepository;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public void run() {
        logger.info("🔄 QueueWorker-{} started", workerId);
        
        while (!shutdown && !Thread.currentThread().isInterrupted()) {
            try {
                TelemetryQueueItem item = queue.take(); // Blocks until available
                
                if (item == null) break; // Shutdown signal
                
                processItem(item);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("❌ QueueWorker-{} processing error", workerId, e);
            }
        }
        
        logger.info("⏹️ QueueWorker-{} stopped", workerId);
    }
    
    private void processItem(TelemetryQueueItem item) {
        try {
            // Calculate wait time inline
            long waitTimeMs = Duration.between(item.queuedAt(), LocalDateTime.now()).toMillis();
            
            // Create entity from record data
            Telemetry telemetry = new Telemetry();
            telemetry.setDeviceId(item.deviceId());
            telemetry.setLatitude(item.latitude());
            telemetry.setLongitude(item.longitude());
            telemetry.setTimestamp(item.timestamp());
            
            // Save to database
            Telemetry savedTelemetry = telemetryRepository.save(telemetry);
            
            // Publish event for async processing
            TelemetryEvent event = new TelemetryEvent(this, savedTelemetry);
            eventPublisher.publishEvent(event);
            
            logger.debug("✅ Worker-{} processed telemetry {} (wait: {}ms)", 
                        workerId, item.deviceId(), waitTimeMs);
            
        } catch (Exception e) {
            logger.error("❌ Worker-{} failed to process telemetry for device {}", 
                        workerId, item.deviceId(), e);
        }
    }
    
    public void shutdown() {
        shutdown = true;
    }
} 