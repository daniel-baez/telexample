package cl.baezdaniel.telexample.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Custom metrics configuration for telemetry system performance monitoring.
 * 
 * This provides:
 * - Endpoint metrics (automatic via Spring Boot)
 * - Custom processor metrics
 * - Thread pool metrics
 * - Business metrics
 */
@Configuration
public class MetricsConfig {
    
    /**
     * Timer for measuring telemetry API processing duration
     */
    @Bean
    public Timer telemetryProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("telemetry.processing.duration")
                .description("Time spent processing telemetry API requests")
                .tag("component", "controller")
                .register(meterRegistry);
    }
    
    /**
     * Timer for measuring anomaly detection duration
     */
    @Bean
    public Timer anomalyDetectionTimer(MeterRegistry meterRegistry) {
        return Timer.builder("telemetry.anomaly.detection.duration")
                .description("Time spent on anomaly detection processing")
                .tag("component", "anomaly-detector")
                .register(meterRegistry);
    }
    
    /**
     * Timer for measuring alert processing duration
     */
    @Bean
    public Timer alertProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("telemetry.alert.processing.duration")
                .description("Time spent processing alerts and notifications")
                .tag("component", "alert-processor")
                .register(meterRegistry);
    }
    
    /**
     * Timer for measuring aggregation processing duration
     */
    @Bean
    public Timer aggregationTimer(MeterRegistry meterRegistry) {
        return Timer.builder("telemetry.aggregation.duration")
                .description("Time spent on data aggregation processing")
                .tag("component", "aggregator")
                .register(meterRegistry);
    }
    
    /**
     * Counter for telemetry events received via API
     */
    @Bean
    public Counter telemetryEventsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("telemetry.events.received")
                .description("Total number of telemetry events received via API")
                .tag("component", "controller")
                .register(meterRegistry);
    }
    
    /**
     * Counter for telemetry events published to async processing
     */
    @Bean
    public Counter telemetryEventsPublishedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("telemetry.events.published")
                .description("Total number of telemetry events published for async processing")
                .tag("component", "controller")
                .register(meterRegistry);
    }
    
    /**
     * Counter for anomalies detected
     */
    @Bean
    public Counter anomaliesDetectedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("telemetry.anomalies.detected")
                .description("Total number of anomalies detected in telemetry data")
                .tag("component", "anomaly-detector")
                .register(meterRegistry);
    }
    
    /**
     * Counter for alerts created
     */
    @Bean
    public Counter alertsCreatedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("telemetry.alerts.created")
                .description("Total number of alerts created by the system")
                .tag("component", "alert-processor")
                .register(meterRegistry);
    }
    
    /**
     * Counter for geofence violations detected
     */
    @Bean
    public Counter geofenceViolationsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("telemetry.geofence.violations")
                .description("Total number of geofence violations detected")
                .tag("component", "alert-processor")
                .tag("type", "geofence")
                .register(meterRegistry);
    }
    
    /**
     * Counter for data aggregation operations
     */
    @Bean
    public Counter aggregationOperationsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("telemetry.aggregation.operations")
                .description("Total number of data aggregation operations performed")
                .tag("component", "aggregator")
                .register(meterRegistry);
    }
    
    /**
     * Register thread pool metrics - active threads
     */
    @Bean
    public Gauge threadPoolActiveThreads(MeterRegistry meterRegistry, ThreadPoolTaskExecutor telemetryTaskExecutor) {
        return Gauge.builder("telemetry.threadpool.active", telemetryTaskExecutor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active threads in telemetry processing thread pool")
                .tag("pool", "telemetry")
                .register(meterRegistry);
    }
    
    /**
     * Register thread pool metrics - queue size
     */
    @Bean
    public Gauge threadPoolQueueSize(MeterRegistry meterRegistry, ThreadPoolTaskExecutor telemetryTaskExecutor) {
        return Gauge.builder("telemetry.threadpool.queue.size", telemetryTaskExecutor, 
                    executor -> executor.getThreadPoolExecutor().getQueue().size())
                .description("Queue size of telemetry processing thread pool")
                .tag("pool", "telemetry")
                .register(meterRegistry);
    }
    
    /**
     * Register thread pool metrics - completed tasks
     */
    @Bean
    public Gauge threadPoolCompletedTasks(MeterRegistry meterRegistry, ThreadPoolTaskExecutor telemetryTaskExecutor) {
        return Gauge.builder("telemetry.threadpool.completed", telemetryTaskExecutor, 
                    executor -> (double) executor.getThreadPoolExecutor().getCompletedTaskCount())
                .description("Completed tasks in telemetry processing thread pool")
                .tag("pool", "telemetry")
                .register(meterRegistry);
    }
} 