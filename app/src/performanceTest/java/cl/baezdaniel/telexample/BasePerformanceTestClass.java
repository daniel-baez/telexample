package cl.baezdaniel.telexample;

import cl.baezdaniel.telexample.services.TelemetryQueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("performance")
@TestPropertySource(properties = {
    "spring.profiles.active=performance",
    "logging.level.cl.baezdaniel.telexample=INFO"
})
public abstract class BasePerformanceTestClass {

    private final List<ExecutorService> executors = new ArrayList<>();

    @Autowired(required = false)
    private TelemetryQueueService queueService;

    @BeforeEach
    void basePerformanceSetUp() {
        // Common performance test setup
        executors.clear();
        System.out.println("🚀 Starting performance test: " + getClass().getSimpleName());
        
        // Log queue service status
        if (queueService != null && queueService.isEnabled()) {
            TelemetryQueueService.QueueStats stats = queueService.getStats();
            System.out.println("📊 Queue service status: " + stats);
        }
    }

    @AfterEach
    void basePerformanceTearDown() {
        // Clean up all registered executors
        shutdownExecutors();
        
        // Clean up TelemetryQueueService workers
        shutdownQueueService();
        
        System.out.println("✅ Completed performance test: " + getClass().getSimpleName());
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
     * Create and register an executor with custom name for automatic cleanup
     */
    protected ExecutorService createManagedExecutor(int threadCount, String name) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, 
            r -> new Thread(r, name + "-" + Thread.currentThread().getId()));
        registerExecutor(executor);
        return executor;
    }

    /**
     * Shutdown all registered executors with enhanced logging
     */
    private void shutdownExecutors() {
        if (executors.isEmpty()) {
            return;
        }

        System.out.printf("🛑 Shutting down %d executor(s)...%n", executors.size());
        
        for (int i = 0; i < executors.size(); i++) {
            ExecutorService executor = executors.get(i);
            if (executor != null && !executor.isShutdown()) {
                System.out.printf("  Shutting down executor %d/%d...%n", i + 1, executors.size());
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        System.out.printf("  Forcing shutdown of executor %d/%d...%n", i + 1, executors.size());
                        executor.shutdownNow();
                        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                            System.err.printf("  ❌ Executor %d/%d did not terminate gracefully%n", i + 1, executors.size());
                        } else {
                            System.out.printf("  ✅ Executor %d/%d terminated%n", i + 1, executors.size());
                        }
                    } else {
                        System.out.printf("  ✅ Executor %d/%d terminated gracefully%n", i + 1, executors.size());
                    }
                } catch (InterruptedException e) {
                    System.out.printf("  ⚠️ Interrupted while shutting down executor %d/%d%n", i + 1, executors.size());
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        executors.clear();
        System.out.println("✅ All executors shut down");
    }

    /**
     * Shutdown TelemetryQueueService worker threads with enhanced logging for performance tests
     */
    private void shutdownQueueService() {
        if (queueService != null && queueService.isEnabled()) {
            try {
                // Log final stats before shutdown
                TelemetryQueueService.QueueStats finalStats = queueService.getStats();
                System.out.println("📊 Final queue stats before shutdown: " + finalStats);
                
                // Use immediate shutdown for performance tests to avoid long waits
                System.out.println("🛑 Shutting down TelemetryQueueService workers...");
                queueService.immediateShutdown();
                System.out.println("✅ TelemetryQueueService shut down successfully");
            } catch (Exception e) {
                System.err.println("⚠️ Error shutting down TelemetryQueueService: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("ℹ️ TelemetryQueueService not enabled or not available");
        }
    }
} 