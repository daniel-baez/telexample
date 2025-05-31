package cl.baezdaniel.telexample.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance telemetry queue service.
 * 
 * This service manages an in-memory queue for telemetry data and coordinates
 * background worker threads for processing. Designed for maximum throughput
 * with configurable capacity and graceful degradation.
 */
@Service
public class TelemetryQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(TelemetryQueueService.class);
    
    // HashMap keys for telemetry queue items
    public static final String KEY_DEVICE_ID = "deviceId";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_QUEUED_AT = "queuedAt";
    public static final String KEY_REQUEST_ID = "requestId";
    
    // Configuration
    @Value("${telemetry.queue.enabled:false}")
    private boolean queueEnabled;
    
    @Value("${telemetry.queue.capacity:10000}")
    private int queueCapacity;
    
    @Value("${telemetry.queue.workers:8}")
    private int workerCount;
    
    // Core queue and worker management
    private BlockingQueue<Map<String, Object>> queue;
    private List<QueueWorker> workers;
    private List<Thread> workerThreads;
    private volatile boolean running = false;
    
    // Metrics (simple counters for now)
    private final AtomicLong enqueuedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong overflowCount = new AtomicLong(0);
    
    @Autowired
    private QueueWorkerFactory queueWorkerFactory;
    
    @PostConstruct
    public void initialize() {
        if (!queueEnabled) {
            logger.info("🚀 Queue-based processing is DISABLED - using synchronous processing");
            return;
        }
        
        logger.info("🚀 Initializing high-performance telemetry queue (capacity: {}, workers: {})", 
                   queueCapacity, workerCount);
        
        // Initialize queue
        queue = new LinkedBlockingQueue<>(queueCapacity);
        workers = new ArrayList<>();
        workerThreads = new ArrayList<>();
        
        // Start worker threads
        startWorkers();
        
        logger.info("✅ Telemetry queue service started successfully - Ready for high throughput!");
    }
    
    private void startWorkers() {
        running = true;
        
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "TelemetryQueueWorker-" + threadNumber.getAndIncrement());
                t.setDaemon(false); // Ensure graceful shutdown
                return t;
            }
        };
        
        for (int i = 0; i < workerCount; i++) {
            QueueWorker worker = queueWorkerFactory.createWorker(queue, this::onItemProcessed);
            workers.add(worker);
            
            Thread workerThread = threadFactory.newThread(worker);
            workerThreads.add(workerThread);
            workerThread.start();
            
            logger.info("📦 Started queue worker thread: {}", workerThread.getName());
        }
    }
    
    /**
     * Attempt to enqueue telemetry data for background processing
     * 
     * @param item The telemetry queue item to process
     * @return true if successfully enqueued, false if queue is full
     */
    public boolean offer(Map<String, Object> item) {
        if (!queueEnabled || queue == null) {
            return false; // Indicates caller should use sync processing
        }
        
        boolean enqueued = queue.offer(item);
        
        if (enqueued) {
            enqueuedCount.incrementAndGet();
            
            logger.debug("✅ Enqueued telemetry for device {} (requestId: {}, queueSize: {})",
                       item.get(KEY_DEVICE_ID), item.get(KEY_REQUEST_ID), queue.size());
        } else {
            overflowCount.incrementAndGet();
            
            logger.warn("⚠️ Queue overflow! Failed to enqueue telemetry for device {} (queueSize: {})",
                       item.get(KEY_DEVICE_ID), queue.size());
        }
        
        return enqueued;
    }
    
    /**
     * Get current queue size for monitoring
     */
    public int size() {
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * Check if queue-based processing is enabled
     */
    public boolean isEnabled() {
        return queueEnabled && running;
    }
    
    /**
     * Get queue statistics for monitoring
     */
    public QueueStats getStats() {
        return new QueueStats(
            size(),
            enqueuedCount.get(),
            processedCount.get(),
            overflowCount.get(),
            queueCapacity,
            workerCount,
            running
        );
    }
    
    /**
     * Callback when worker processes an item (for metrics)
     */
    private void onItemProcessed() {
        processedCount.incrementAndGet();
    }
    
    @PreDestroy
    public void shutdown() {
        if (!queueEnabled || !running) {
            return;
        }
        
        logger.info("🛑 Shutting down telemetry queue service...");
        running = false;
        
        // Stop all workers
        workers.forEach(QueueWorker::stop);
        
        // Wait for worker threads to finish (with timeout)
        for (Thread workerThread : workerThreads) {
            try {
                workerThread.join(5000); // 5 second timeout
                logger.info("✅ Worker thread {} stopped", workerThread.getName());
            } catch (InterruptedException e) {
                logger.warn("⚠️ Interrupted while stopping worker thread {}", workerThread.getName());
                Thread.currentThread().interrupt();
            }
        }
        
        // Log final statistics
        QueueStats finalStats = getStats();
        logger.info("📊 Final queue statistics: {}", finalStats);
        
        logger.info("✅ Telemetry queue service shutdown complete");
    }
    
    /**
     * Queue statistics for monitoring and debugging
     */
    public static class QueueStats {
        private final int currentSize;
        private final long totalEnqueued;
        private final long totalProcessed;
        private final long totalOverflow;
        private final int capacity;
        private final int workerCount;
        private final boolean running;
        
        public QueueStats(int currentSize, long totalEnqueued, long totalProcessed, 
                         long totalOverflow, int capacity, int workerCount, boolean running) {
            this.currentSize = currentSize;
            this.totalEnqueued = totalEnqueued;
            this.totalProcessed = totalProcessed;
            this.totalOverflow = totalOverflow;
            this.capacity = capacity;
            this.workerCount = workerCount;
            this.running = running;
        }
        
        // Getters
        public int getCurrentSize() { return currentSize; }
        public long getTotalEnqueued() { return totalEnqueued; }
        public long getTotalProcessed() { return totalProcessed; }
        public long getTotalOverflow() { return totalOverflow; }
        public int getCapacity() { return capacity; }
        public int getWorkerCount() { return workerCount; }
        public boolean isRunning() { return running; }
        
        public double getUtilizationPercent() {
            return capacity > 0 ? (currentSize * 100.0) / capacity : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("QueueStats{size=%d/%d (%.1f%%), enqueued=%d, processed=%d, overflow=%d, workers=%d, running=%s}",
                currentSize, capacity, getUtilizationPercent(), totalEnqueued, totalProcessed, totalOverflow, workerCount, running);
        }
    }
} 