package cl.baezdaniel.telexample.processors;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import cl.baezdaniel.telexample.services.AlertService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async processors for telemetry events.
 * Each method runs in parallel on the configured thread pool.
 * Instrumented with comprehensive metrics for performance monitoring.
 */
@Component
public class TelemetryProcessors {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryProcessors.class);

    private final AlertService alertService;
    
    // Metrics
    private final Timer anomalyDetectionTimer;
    private final Timer alertProcessingTimer;
    private final Timer aggregationTimer;
    private final Counter anomaliesDetectedCounter;
    private final Counter alertsCreatedCounter;
    private final Counter geofenceViolationsCounter;
    private final Counter aggregationOperationsCounter;

    @Autowired
    public TelemetryProcessors(AlertService alertService,
                              Timer anomalyDetectionTimer,
                              Timer alertProcessingTimer,
                              Timer aggregationTimer,
                              Counter anomaliesDetectedCounter,
                              Counter alertsCreatedCounter,
                              Counter geofenceViolationsCounter,
                              Counter aggregationOperationsCounter) {
        this.alertService = alertService;
        this.anomalyDetectionTimer = anomalyDetectionTimer;
        this.alertProcessingTimer = alertProcessingTimer;
        this.aggregationTimer = aggregationTimer;
        this.anomaliesDetectedCounter = anomaliesDetectedCounter;
        this.alertsCreatedCounter = alertsCreatedCounter;
        this.geofenceViolationsCounter = geofenceViolationsCounter;
        this.aggregationOperationsCounter = aggregationOperationsCounter;
    }

    /**
     * Processor 1: Anomaly Detection
     * Detects data anomalies and suspicious patterns in telemetry data
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void detectAnomaly(TelemetryEvent event) {
        Timer.Sample sample = Timer.start();
        
        try {
            if (event == null || event.getTelemetry() == null) {
                logger.error("Error in anomaly detection: Received null telemetry event");
                return;
            }
            
            Telemetry telemetry = event.getTelemetry();
            logger.info("🔍 [Thread: {}] Processing anomaly detection for device: {}", 
                       Thread.currentThread().getName(), telemetry.getDeviceId());
            
            // Anomaly detection logic
            if (isInvalidCoordinates(telemetry.getLatitude(), telemetry.getLongitude())) {
                // Increment anomaly counter
                anomaliesDetectedCounter.increment();
                
                logger.warn("🚨 ANOMALY DETECTED: Invalid coordinates detected: lat={}, lon={}", 
                           telemetry.getLatitude(), telemetry.getLongitude());
                
                alertService.createAlert(
                    telemetry.getDeviceId(),
                    "ANOMALY",
                    "Invalid coordinates detected",
                    telemetry.getLatitude(),
                    telemetry.getLongitude(),
                    "AnomDetProcessor",
                    null
                );
                
                // Increment alert counter
                alertsCreatedCounter.increment();
                
                logger.info("🚨 Alert created for device {}: Invalid coordinates", telemetry.getDeviceId());
            }
            
            if (isExtremeLocation(telemetry.getLatitude())) {
                // Increment anomaly counter
                anomaliesDetectedCounter.increment();
                
                logger.warn("🚨 ANOMALY DETECTED: Extreme latitude detected: {}", telemetry.getLatitude());
                
                alertService.createAlert(
                    telemetry.getDeviceId(),
                    "ANOMALY", 
                    "Extreme location detected",
                    telemetry.getLatitude(),
                    telemetry.getLongitude(),
                    "AnomDetProcessor",
                    null
                );
                
                // Increment alert counter
                alertsCreatedCounter.increment();
                
                logger.info("🚨 Alert created for device {}: Extreme location", telemetry.getDeviceId());
            }
            
        } catch (Exception e) {
            logger.error("Error in anomaly detection: {}", e.getMessage(), e);
        } finally {
            sample.stop(anomalyDetectionTimer);
        }
    }

    /**
     * Alert System: Process real-time alerts and notifications
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void processAlerts(TelemetryEvent event) {
        Timer.Sample sample = Timer.start();
        
        try {
            System.out.println("processAlerts");
            logger.info("processAlerts");
            if (event == null || event.getTelemetry() == null) {
                logger.error("Error in alert processing: Received null telemetry event");
                return;
            }

            Telemetry telemetry = event.getTelemetry();
            String deviceId = telemetry.getDeviceId();

            logger.info("🔔 [Thread: {}] Processing alerts for device: {}",
                    Thread.currentThread().getName(), deviceId);

            // Example geofencing logic - check if device is in restricted area
            double lat = telemetry.getLatitude();
            double lon = telemetry.getLongitude();

            // Define restricted area (example: around coordinates 40.7589, -73.9851)
            double restrictedLat = 40.7589;
            double restrictedLon = -73.9851;
            double distanceKm = calculateDistanceKm(lat, lon, restrictedLat, restrictedLon);
            double radiusKm = 1.0; // 1 km radius

            if (distanceKm <= radiusKm) {
                // Increment geofence violation counter
                geofenceViolationsCounter.increment();
                
                System.out.println("distance <= radius");
                String message = String.format("Device entered restricted area: distance=%.4f from restricted zone",
                        distanceKm);
                logger.warn("🚨 GEOFENCE ALERT: {}", message);

                // Create geofence alert
                try {
                    alertService.createAlert(
                        deviceId,
                        "GEOFENCE",
                        message,
                        lat,
                        lon,
                        "AlertProcessor",
                        String.format(
                            "{\"restrictedZone\": {\"lat\": %s, \"lon\": %s}, \"distance\": %.4f}",
                            restrictedLat, restrictedLon, distanceKm
                        )
                    );
                    
                    // Increment alert counter
                    alertsCreatedCounter.increment();
                    
                    logger.info("🚨 Alert created for device {}: Geofence violation", deviceId);
                } catch (Exception e) {
                    logger.error("Failed to create geofence alert for device {}: {}", deviceId, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error in alert processing for device {}: {}", 
                event != null && event.getTelemetry() != null ? event.getTelemetry().getDeviceId() : "unknown", 
                e.getMessage(), e);
        } finally {
            sample.stop(alertProcessingTimer);
        }
    }

    /**
     * Data Aggregation: Combine and aggregate telemetry data
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void aggregateData(TelemetryEvent event) {
        Timer.Sample sample = Timer.start();
        
        try {
            if (event == null || event.getTelemetry() == null) {
                logger.error("Error in data aggregation: Received null telemetry event");
                return;
            }

            Telemetry telemetry = event.getTelemetry();
            String deviceId = telemetry.getDeviceId();

            logger.info("🗺️ [Thread: {}] Processing aggregation for device: {}",
                    Thread.currentThread().getName(), deviceId);

            // Example aggregation logic
            double lat = telemetry.getLatitude();
            double lon = telemetry.getLongitude();

            logger.info("🗺️ Aggregated coordinates for device {}: lat={}, lon={}", deviceId, lat, lon);
            
            // Increment aggregation operations counter
            aggregationOperationsCounter.increment();
            
        } catch (Exception e) {
            logger.error("Error in data aggregation for device {}: {}", 
                event != null && event.getTelemetry() != null ? event.getTelemetry().getDeviceId() : "unknown", 
                e.getMessage(), e);
        } finally {
            sample.stop(aggregationTimer);
        }
    }

    // Helper methods

    private boolean isInvalidCoordinates(double lat, double lon) {
        return lat < -90 || lat > 90 || lon < -180 || lon > 180;
    }

    private boolean isExtremeLocation(double lat) {
        return Math.abs(lat) > 80;
    }

    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in kilometers
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // Distance in kilometers
    }
}