package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Factory for creating QueueWorker instances with proper Spring dependency injection.
 * 
 * This factory ensures that each worker gets access to the required Spring beans
 * (repository, event publisher) while being created programmatically for the queue service.
 */
@Component
public class QueueWorkerFactory {
    
    @Autowired
    private TelemetryRepository telemetryRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    /**
     * Create a new queue worker instance
     * 
     * @param queue The blocking queue to consume from
     * @param onProcessedCallback Callback to invoke when an item is processed
     * @return A configured QueueWorker instance
     */
    public QueueWorker createWorker(BlockingQueue<Map<String, Object>> queue, Runnable onProcessedCallback) {
        return new QueueWorker(queue, telemetryRepository, eventPublisher, onProcessedCallback);
    }
} 