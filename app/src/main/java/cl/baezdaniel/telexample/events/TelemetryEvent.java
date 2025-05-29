package cl.baezdaniel.telexample.events;

import cl.baezdaniel.telexample.entities.Telemetry;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when new telemetry data is received and saved.
 * This event triggers async processing like anomaly detection and analytics.
 */
public class TelemetryEvent extends ApplicationEvent {
    
    private final Telemetry telemetry;
    private final long processingStartTime;
    
    public TelemetryEvent(Object source, Telemetry telemetry) {
        super(source);
        this.telemetry = telemetry;
        this.processingStartTime = System.currentTimeMillis();
    }
    
    public Telemetry getTelemetry() {
        return telemetry;
    }
    
    public long getProcessingStartTime() {
        return processingStartTime;
    }
} 