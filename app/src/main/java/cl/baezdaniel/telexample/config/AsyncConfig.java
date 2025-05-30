package cl.baezdaniel.telexample.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async telemetry event processing.
 * Thread pool size is configurable for vertical scalability tuning.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Value("${telemetry.processing.core-pool-size:4}")
    private int corePoolSize;
    
    @Value("${telemetry.processing.max-pool-size:8}")
    private int maxPoolSize;
    
    @Value("${telemetry.processing.queue-capacity:100}")
    private int queueCapacity;
    
    @Bean(name = "telemetryTaskExecutor")
    public Executor telemetryTaskExecutor() {
        // Original async executor configuration
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("TelemetryProcessor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;


        // For testing: Execute everything in the current thread
        // return new Executor() {
        //     @Override
        //     public void execute(Runnable command) {
        //         command.run();
        //     }
        // };
    }
} 