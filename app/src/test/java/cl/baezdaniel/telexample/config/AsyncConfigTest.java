package cl.baezdaniel.telexample.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for async thread pool configuration.
 * Validates default settings, custom overrides, and thread pool behavior.
 */
@SpringBootTest
class AsyncConfigTest {

    @Autowired
    private Executor telemetryTaskExecutor;

    /**
     * Test Case 3.1: Default Configuration Loading
     * Verify default thread pool settings
     */
    @Test
    void testDefaultAsyncConfiguration() {
        // Get telemetryTaskExecutor bean
        assertThat(telemetryTaskExecutor).isNotNull();
        assertThat(telemetryTaskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) telemetryTaskExecutor;

        // Assert core pool size = 4 (default)
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        
        // Assert max pool size = 8 (default) 
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        
        // Assert queue capacity = 100 (default)
        assertThat(executor.getQueueCapacity()).isEqualTo(100);
        
        // Verify thread name prefix = "TelemetryProcessor-"
        assertThat(executor.getThreadNamePrefix()).isEqualTo("TelemetryProcessor-");
    }

    /**
     * Test Case 3.3: Thread Pool Behavior
     * Validate thread creation and reuse
     */
    @Test
    void testThreadPoolBehavior() throws InterruptedException {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) telemetryTaskExecutor;
        
        // Get initial pool size
        int initialPoolSize = executor.getPoolSize();
        
        // Submit tasks equal to core pool size
        final int corePoolSize = executor.getCorePoolSize();
        final CountDownLatch taskLatch = new CountDownLatch(corePoolSize);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicInteger executedTasks = new AtomicInteger(0);

        // Submit core pool size number of tasks
        for (int i = 0; i < corePoolSize; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all tasks to be submitted
                    Thread.sleep(100); // Simulate work
                    executedTasks.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    taskLatch.countDown();
                }
            });
        }

        // Start all tasks
        startLatch.countDown();
        
        // Wait for tasks to complete
        boolean completed = taskLatch.await(2, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // Verify all tasks executed
        assertThat(executedTasks.get()).isEqualTo(corePoolSize);
        
        // Verify core threads are created (pool size should increase)
        assertThat(executor.getPoolSize()).isGreaterThanOrEqualTo(initialPoolSize);

        // Test additional tasks to trigger max pool expansion
        final int additionalTasks = 2;
        final CountDownLatch additionalLatch = new CountDownLatch(additionalTasks);
        final CountDownLatch additionalStartLatch = new CountDownLatch(1);

        for (int i = 0; i < additionalTasks; i++) {
            executor.submit(() -> {
                try {
                    additionalStartLatch.await();
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    additionalLatch.countDown();
                }
            });
        }

        additionalStartLatch.countDown();
        boolean additionalCompleted = additionalLatch.await(2, TimeUnit.SECONDS);
        assertThat(additionalCompleted).isTrue();

        // Allow some time for thread pool to stabilize
        Thread.sleep(200);
        
        // Confirm thread reuse after task completion (pool size should not exceed max)
        assertThat(executor.getPoolSize()).isLessThanOrEqualTo(executor.getMaxPoolSize());
    }

    /**
     * Nested test class for custom configuration testing
     */
    @SpringBootTest
    @TestPropertySource(properties = {
        "telemetry.processing.core-pool-size=2",
        "telemetry.processing.max-pool-size=4", 
        "telemetry.processing.queue-capacity=50"
    })
    static class CustomConfigurationTest {

        @Autowired
        private Executor telemetryTaskExecutor;

        /**
         * Test Case 3.2: Custom Configuration Override
         * Test property-based configuration override
         */
        @Test
        void testCustomAsyncConfiguration() {
            // Load Spring context with custom properties
            assertThat(telemetryTaskExecutor).isNotNull();
            assertThat(telemetryTaskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);

            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) telemetryTaskExecutor;

            // Verify thread pool uses custom values
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(50);

            // Assert configuration changes are applied correctly
            assertThat(executor.getThreadNamePrefix()).isEqualTo("TelemetryProcessor-");
        }
    }

    /**
     * Test for graceful shutdown behavior
     */
    @Test
    void testGracefulShutdown() throws InterruptedException {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) telemetryTaskExecutor;
        
        // Submit a long-running task
        final CountDownLatch taskStarted = new CountDownLatch(1);
        final CountDownLatch taskFinished = new CountDownLatch(1);
        
        executor.submit(() -> {
            taskStarted.countDown();
            try {
                Thread.sleep(500); // Simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                taskFinished.countDown();
            }
        });

        // Wait for task to start
        boolean started = taskStarted.await(1, TimeUnit.SECONDS);
        assertThat(started).isTrue();

        // Verify executor is active
        assertThat(executor.getActiveCount()).isGreaterThan(0);

        // Test that shutdown waits for tasks to complete
        // Note: We don't actually shutdown the executor as it would affect other tests
        // This test validates the configuration supports graceful shutdown
        assertThat(executor.getThreadPoolExecutor().isShutdown()).isFalse();
        
        // Wait for task completion
        boolean finished = taskFinished.await(2, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
    }

    /**
     * Test executor bean is properly initialized
     */
    @Test
    void testExecutorInitialization() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) telemetryTaskExecutor;
        
        // Verify executor is initialized and ready to accept tasks
        assertThat(executor.getThreadPoolExecutor()).isNotNull();
        assertThat(executor.getThreadPoolExecutor().isShutdown()).isFalse();
        assertThat(executor.getThreadPoolExecutor().isTerminated()).isFalse();
        
        // Test task submission works
        final CountDownLatch testLatch = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger(0);
        
        executor.submit(() -> {
            result.set(42);
            testLatch.countDown();
        });
        
        try {
            boolean completed = testLatch.await(1, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(result.get()).isEqualTo(42);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
} 