package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.dto.TelemetryQueueItem;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
     * Create a new QueueWorker instance with injected dependencies
     */
    public QueueWorker createWorker(int workerId, BlockingQueue<TelemetryQueueItem> queue) {
        return new QueueWorker(workerId, queue, telemetryRepository, eventPublisher);
    }
} 