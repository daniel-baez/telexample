package cl.baezdaniel.telexample.processors;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async processors for telemetry events.
 * Each method runs in parallel on the configured thread pool.
 */
@Component
public class TelemetryProcessors {
    
    private static final Logger logger = LoggerFactory.getLogger(TelemetryProcessors.class);
    
    /**
     * Anomaly Detection: Check for suspicious speed changes or location jumps
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void detectAnomalies(TelemetryEvent event) {
        if (event == null || event.getTelemetry() == null) {
            logger.error("Error in anomaly detection: Received null telemetry event");
            return;
        }
        
        Telemetry telemetry = event.getTelemetry();
        String deviceId = telemetry.getDeviceId();
        
        try {
            logger.info("🔍 [Thread: {}] Processing anomaly detection for device: {}", 
                Thread.currentThread().getName(), deviceId);
            
            // Simulate anomaly detection logic
            double lat = telemetry.getLatitude();
            double lon = telemetry.getLongitude();
            
            // Example: Check if coordinates are outside expected ranges
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                logger.warn("🚨 ANOMALY DETECTED: Invalid coordinates for device {}: lat={}, lon={}", 
                    deviceId, lat, lon);
            }
            
            // Example: Check for rapid movement (would need previous location in real implementation)
            // This is just a demo - you'd fetch previous telemetry and calculate distance/time
            if (Math.abs(lat) > 80) { // Extreme latitude
                logger.warn("🚨 ANOMALY DETECTED: Extreme latitude for device {}: {}", deviceId, lat);
            }
            
            
        } catch (Exception e) {
            logger.error("Error in anomaly detection for device {}: {}", deviceId, e.getMessage(), e);
        }
    }
    
    /**
     * Statistics Aggregation: Update device statistics and metrics
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void updateStatistics(TelemetryEvent event) {
        if (event == null || event.getTelemetry() == null) {
            logger.error("Error updating statistics: Received null telemetry event");
            return;
        }
        
        Telemetry telemetry = event.getTelemetry();
        String deviceId = telemetry.getDeviceId();
        
        try {
            logger.info("📊 [Thread: {}] Processing statistics for device: {}", 
                Thread.currentThread().getName(), deviceId);
            
            // Simulate statistics processing
            long processingDelay = System.currentTimeMillis() - event.getProcessingStartTime();
            
            // Example metrics you might track:
            // - Total telemetry points per device
            // - Average location updates per hour
            // - Device active time windows
            // - Geographic coverage area
            
            logger.info("📈 Statistics updated for device {}, processing delay: {}ms", 
                deviceId, processingDelay);
            
            
        } catch (Exception e) {
            logger.error("Error updating statistics for device {}: {}", deviceId, e.getMessage());
        }
    }
    
    /**
     * Alert System: Send notifications for important events
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void processAlerts(TelemetryEvent event) {
        if (event == null || event.getTelemetry() == null) {
            logger.error("Error processing alerts: Received null telemetry event");
            return;
        }
        
        Telemetry telemetry = event.getTelemetry();
        String deviceId = telemetry.getDeviceId();
        
        try {
            logger.info("🔔 [Thread: {}] Processing alerts for device: {}", 
                Thread.currentThread().getName(), deviceId);
            
            // Example alert conditions
            double lat = telemetry.getLatitude();
            double lon = telemetry.getLongitude();
            
            // Geofencing example: Alert if device enters/exits certain areas
            if (isInRestrictedArea(lat, lon)) {
                logger.warn("🚨 ALERT: Device {} entered restricted area: lat={}, lon={}", 
                    deviceId, lat, lon);
                // In real implementation: send push notification, email, SMS, etc.
            }
            
            
        } catch (Exception e) {
            logger.error("Error processing alerts for device {}: {}", deviceId, e.getMessage());
        }
    }
    
    /**
     * Map-Reduce Style Processing: Aggregate telemetry data for analytics
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void aggregateData(TelemetryEvent event) {
        if (event == null || event.getTelemetry() == null) {
            logger.error("Error aggregating data: Received null telemetry event");
            return;
        }
        
        Telemetry telemetry = event.getTelemetry();
        String deviceId = telemetry.getDeviceId();
        
        try {
            logger.info("🗺️ [Thread: {}] Processing aggregation for device: {}", 
                Thread.currentThread().getName(), deviceId);
            
            // Example aggregations:
            // - Hourly device position summaries
            // - Regional device density maps  
            // - Movement pattern analysis
            // - Performance metrics rollups
            
            logger.info("📍 Data aggregated for device {} at coordinates: [{}, {}]", 
                deviceId, telemetry.getLatitude(), telemetry.getLongitude());
            
            
        } catch (Exception e) {
            logger.error("Error aggregating data for device {}: {}", deviceId, e.getMessage());
        }
    }
    
    private boolean isInRestrictedArea(double lat, double lon) {
        // Example: Simple rectangular geofence
        // In real implementation, you'd use proper geospatial libraries
        return (lat >= 40.0 && lat <= 41.0 && lon >= -74.5 && lon <= -73.5);
    }
} 