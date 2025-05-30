package cl.baezdaniel.telexample.processors;

import cl.baezdaniel.telexample.dto.AlertCreationRequest;
import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import cl.baezdaniel.telexample.services.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private AlertService alertService;

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
                String message = String.format("Invalid coordinates detected: lat=%s, lon=%s", lat, lon);
                logger.warn("🚨 ANOMALY DETECTED: {}", message);

                // Create alert for invalid coordinates
                try {
                    AlertCreationRequest alertRequest = AlertCreationRequest.builder()
                            .deviceId(deviceId)
                            .alertType("ANOMALY")
                            .message(message)
                            .latitude(lat)
                            .longitude(lon)
                            .processorName("AnomalyDetection")
                            .metadata(createMetadataJson(telemetry))
                            .build();

                    alertService.createAlert(alertRequest);
                    logger.info("🚨 Alert created for device {}: Invalid coordinates", deviceId);
                } catch (Exception e) {
                    logger.error("Failed to create alert for device {}: {}", deviceId, e.getMessage());
                }
            }

            // Example: Check for rapid movement (would need previous location in real
            // implementation)
            // This is just a demo - you'd fetch previous telemetry and calculate
            // distance/time
            if (Math.abs(lat) > 80) { // Extreme latitude
                String message = String.format("Extreme latitude detected: %s", lat);
                logger.warn("🚨 ANOMALY DETECTED: {}", message);

                // Create alert for extreme location
                try {
                    AlertCreationRequest alertRequest = AlertCreationRequest.builder()
                            .deviceId(deviceId)
                            .alertType("ANOMALY")
                            .message(message)
                            .latitude(lat)
                            .longitude(lon)
                            .processorName("AnomalyDetection")
                            .metadata(createMetadataJson(telemetry))
                            .build();

                    alertService.createAlert(alertRequest);
                    logger.info("🚨 Alert created for device {}: Extreme location", deviceId);
                } catch (Exception e) {
                    logger.error("Failed to create alert for device {}: {}", deviceId, e.getMessage());
                }
            }

            // Simulate processing time

        } catch (Exception e) {
            logger.error("Error in anomaly detection for device {}: {}", deviceId, e.getMessage());
        }
    }


    /**
     * Alert System: Process real-time alerts and notifications
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void processAlerts(TelemetryEvent event) {
        System.out.println("processAlerts");
        logger.info("processAlerts");
        if (event == null || event.getTelemetry() == null) {
            logger.error("Error in alert processing: Received null telemetry event");
            return;
        }

        Telemetry telemetry = event.getTelemetry();
        String deviceId = telemetry.getDeviceId();

        try {
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
                System.out.println("distance <= radius");
                String message = String.format("Device entered restricted area: distance=%.4f from restricted zone",
                        distanceKm);
                logger.warn("🚨 GEOFENCE ALERT: {}", message);

                // Create geofence alert
                try {
                    AlertCreationRequest alertRequest = AlertCreationRequest.builder()
                            .deviceId(deviceId)
                            .alertType("GEOFENCE")
                            .message(message)
                            .latitude(lat)
                            .longitude(lon)
                            .processorName("AlertProcessor")
                            .metadata(String.format(
                                    "{\"restrictedZone\": {\"lat\": %s, \"lon\": %s}, \"distance\": %.4f}",
                                    restrictedLat, restrictedLon, distanceKm))
                            .build();

                    alertService.createAlert(alertRequest);
                    logger.info("🚨 Alert created for device {}: Geofence violation", deviceId);
                } catch (Exception e) {
                    logger.error("Failed to create geofence alert for device {}: {}", deviceId, e.getMessage());
                }
            }

            // Simulate processing time

        } catch (Exception e) {
            logger.error("Error in alert processing for device {}: {}", deviceId, e.getMessage());
        }
    }

    /**
     * Data Aggregation: Combine and aggregate telemetry data
     */
    @EventListener
    @Async("telemetryTaskExecutor")
    public void aggregateData(TelemetryEvent event) {
        if (event == null || event.getTelemetry() == null) {
            logger.error("Error in data aggregation: Received null telemetry event");
            return;
        }

        Telemetry telemetry = event.getTelemetry();
        String deviceId = telemetry.getDeviceId();

        try {
            logger.info("🗺️ [Thread: {}] Processing aggregation for device: {}",
                    Thread.currentThread().getName(), deviceId);

            // Example aggregation logic
            double lat = telemetry.getLatitude();
            double lon = telemetry.getLongitude();

            logger.info("🗺️ Aggregated coordinates for device {}: lat={}, lon={}", deviceId, lat, lon);
        } catch (Exception e) {
            logger.error("Error in data aggregation for device {}: {}", deviceId, e.getMessage());
        }
    }

    // Helper methods

    private String createMetadataJson(Telemetry telemetry) {
        return String.format(
                "{\"timestamp\": \"%s\", \"deviceType\": \"tracker\", \"coordinates\": {\"lat\": %s, \"lon\": %s}}",
                telemetry.getTimestamp(), telemetry.getLatitude(), telemetry.getLongitude());
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