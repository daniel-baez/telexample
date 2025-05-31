package cl.baezdaniel.telexample;

import cl.baezdaniel.telexample.services.TelemetryQueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseTestClass {

    private final List<ExecutorService> executors = new ArrayList<>();

    @Autowired(required = false)
    private TelemetryQueueService queueService;

    @BeforeEach
    void baseSetUp() {
        // Common setup logic
        executors.clear();
    }

    @AfterEach
    void baseTearDown() {
        // Clean up all registered executors
        shutdownExecutors();
        
        // Clean up TelemetryQueueService workers
        shutdownQueueService();
    }

    /**
     * Register an executor for automatic cleanup
     */
    protected void registerExecutor(ExecutorService executor) {
        executors.add(executor);
    }

    /**
     * Create and register an executor for automatic cleanup
     */
    protected ExecutorService createManagedExecutor(int threadCount) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        registerExecutor(executor);
        return executor;
    }

    /**
     * Shutdown all registered executors
     */
    private void shutdownExecutors() {
        for (ExecutorService executor : executors) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                            System.err.println("Executor did not terminate gracefully");
                        }
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        executors.clear();
    }

    /**
     * Shutdown TelemetryQueueService worker threads for clean test isolation
     */
    private void shutdownQueueService() {
        if (queueService != null && queueService.isEnabled()) {
            try {
                // Use immediate shutdown for tests to avoid long waits
                queueService.immediateShutdown();
                System.out.println("✅ TelemetryQueueService shut down for test isolation");
            } catch (Exception e) {
                System.err.println("⚠️ Error shutting down TelemetryQueueService: " + e.getMessage());
            }
        }
    }
} 