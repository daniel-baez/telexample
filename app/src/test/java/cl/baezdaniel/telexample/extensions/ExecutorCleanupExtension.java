package cl.baezdaniel.telexample.extensions;

import cl.baezdaniel.telexample.services.TelemetryQueueService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * JUnit 5 Extension that automatically manages ExecutorService and TelemetryQueueService lifecycle.
 * Use with @ExtendWith(ExecutorCleanupExtension.class)
 */
public class ExecutorCleanupExtension implements BeforeEachCallback, AfterEachCallback {

    private static final String EXECUTORS_KEY = "executors";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        List<ExecutorService> executors = new ArrayList<>();
        context.getStore(ExtensionContext.Namespace.create(getClass()))
                .put(EXECUTORS_KEY, executors);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        @SuppressWarnings("unchecked")
        List<ExecutorService> executors = (List<ExecutorService>) context
                .getStore(ExtensionContext.Namespace.create(getClass()))
                .get(EXECUTORS_KEY);

        if (executors != null) {
            shutdownExecutors(executors);
        }
        
        // Also shutdown TelemetryQueueService if available
        shutdownQueueService(context);
    }

    /**
     * Register an executor for automatic cleanup (call from test methods)
     */
    public static void registerExecutor(ExtensionContext context, ExecutorService executor) {
        @SuppressWarnings("unchecked")
        List<ExecutorService> executors = (List<ExecutorService>) context
                .getStore(ExtensionContext.Namespace.create(ExecutorCleanupExtension.class))
                .get(EXECUTORS_KEY);
        
        if (executors != null) {
            executors.add(executor);
        }
    }

    /**
     * Create and register an executor for automatic cleanup
     */
    public static ExecutorService createManagedExecutor(ExtensionContext context, int threadCount) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        registerExecutor(context, executor);
        return executor;
    }

    private void shutdownExecutors(List<ExecutorService> executors) {
        for (ExecutorService executor : executors) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                            System.err.println("Executor did not terminate gracefully: " + executor);
                        }
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Shutdown TelemetryQueueService if available in the Spring context
     */
    private void shutdownQueueService(ExtensionContext context) {
        try {
            // Try to get the Spring application context
            var applicationContext = SpringExtension.getApplicationContext(context);
            
            // Try to get TelemetryQueueService bean
            TelemetryQueueService queueService = applicationContext.getBean(TelemetryQueueService.class);
            
            if (queueService != null && queueService.isEnabled()) {
                queueService.immediateShutdown();
                System.out.println("✅ TelemetryQueueService shut down by ExecutorCleanupExtension");
            }
        } catch (Exception e) {
            // Silently ignore if Spring context is not available or bean doesn't exist
            // This is expected in non-Spring tests
        }
    }
} 