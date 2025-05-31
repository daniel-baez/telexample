package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.entities.Alert;
import cl.baezdaniel.telexample.repositories.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing alerts with improved concurrency handling
 */
@Service
public class AlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    
    @Autowired
    private AlertRepository alertRepository;
    
    @Value("${alert.retention.months:3}")
    private int retentionMonths;
    
    /**
     * Create a new alert with deduplication logic and retry handling for concurrency
     */
    public Alert createAlert(String deviceId, String alertType, String message, 
                           Double latitude, Double longitude, String processorName, String metadata) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID cannot be null or empty");
        }
        if (alertType == null || alertType.trim().isEmpty()) {
            throw new IllegalArgumentException("Alert type cannot be null or empty");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Alert message cannot be null or empty");
        }
        
        return createAlertInternal(deviceId, alertType, message, latitude, longitude, processorName, metadata);
    }
    
    @Transactional
    private Alert createAlertInternal(String deviceId, String alertType, String message, 
                                    Double latitude, Double longitude, String processorName, String metadata) {
        logger.debug("Creating alert for device {}: {}", deviceId, alertType);
        
        // Generate fingerprint for deduplication
        String fingerprint = generateFingerprint(deviceId, alertType, latitude, longitude);
        
        // IMPROVED: Use synchronized block for critical section to prevent race conditions
        synchronized (this) {
            // Check for duplicates within synchronized block
            Optional<Alert> existingAlert = alertRepository.findByFingerprint(fingerprint);
            if (existingAlert.isPresent()) {
                logger.debug("Duplicate alert detected for fingerprint: {}, returning existing", fingerprint);
                return existingAlert.get();
            }
            
            // Determine severity based on alert type and context
            String severity = determineSeverity(alertType, message);
            
            // Create new alert
            Alert alert = new Alert(
                deviceId,
                alertType,
                severity,
                message,
                latitude,
                longitude,
                processorName,
                fingerprint,
                metadata
            );
            
            Alert savedAlert = alertRepository.save(alert);
            logger.info("Created new alert [{}] for device {}: {} ({})", 
                       savedAlert.getId(), savedAlert.getDeviceId(), savedAlert.getAlertType(), savedAlert.getSeverity());
            
            return savedAlert;
        }
    }
    
    /**
     * Get alerts for a specific device with pagination
     */
    public Page<Alert> getAlertsForDevice(String deviceId, Pageable pageable) {
        logger.debug("Fetching alerts for device: {}", deviceId);
        return alertRepository.findByDeviceId(deviceId, pageable);
    }
    
    /**
     * Get alerts with advanced filtering using direct parameters
     */
    public Page<Alert> getAlertsWithFilters(String deviceId, String alertType, String severity, 
                                          LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        logger.debug("Fetching alerts with filters - deviceId: {}, alertType: {}, severity: {}, startDate: {}, endDate: {}", 
                    deviceId, alertType, severity, startDate, endDate);
        
        // Check if all filters are empty/null
        if (isEmptyFilter(deviceId, alertType, severity, startDate, endDate)) {
            return getAllAlerts(pageable);
        }
        
        return alertRepository.findWithFilters(deviceId, alertType, severity, startDate, endDate, pageable);
    }
    
    /**
     * Check if all filter parameters are empty or null
     */
    private boolean isEmptyFilter(String deviceId, String alertType, String severity, 
                                 LocalDateTime startDate, LocalDateTime endDate) {
        return (deviceId == null || deviceId.trim().isEmpty()) && 
               (alertType == null || alertType.trim().isEmpty()) && 
               (severity == null || severity.trim().isEmpty()) && 
               startDate == null && 
               endDate == null;
    }
    
    /**
     * Cleanup old alerts based on retention policy
     */
    @Transactional
    public int cleanupOldAlerts() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(retentionMonths);
        
        long oldAlertCount = alertRepository.countOlderThan(cutoffDate);
        if (oldAlertCount > 0) {
            logger.info("Cleaning up {} alerts older than {} months", oldAlertCount, retentionMonths);
            alertRepository.deleteAlertsOlderThan(cutoffDate);
            logger.info("Successfully deleted {} old alerts", oldAlertCount);
            return (int) oldAlertCount;
        } else {
            logger.debug("No old alerts found for cleanup");
            return 0;
        }
    }
    
    /**
     * Generate MD5 fingerprint for deduplication
     */
    private String generateFingerprint(String deviceId, String alertType, Double latitude, Double longitude) {
        try {
            String context = String.format("%s:%s:%s:%s", 
                deviceId, 
                alertType, 
                latitude != null ? latitude.toString() : "null",
                longitude != null ? longitude.toString() : "null"
            );
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(context.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error generating fingerprint: {}", e.getMessage());
            // Fallback to simple concatenation if MD5 fails
            return String.format("%s-%s-%s-%s", deviceId, alertType, latitude, longitude);
        }
    }
    
    /**
     * Determine alert severity based on type and context
     */
    private String determineSeverity(String alertType, String message) {
        switch (alertType.toUpperCase()) {
            case "ANOMALY":
                if (message.contains("Invalid coordinates")) {
                    return "HIGH";
                } else if (message.contains("Extreme location")) {
                    return "LOW";
                } else if (message.contains("suspicious")) {
                    return "MEDIUM";
                } else {
                    return "LOW";
                }
            
            case "GEOFENCE":
                if (message.contains("restricted") || message.contains("forbidden")) {
                    return "CRITICAL";
                } else {
                    return "MEDIUM";
                }
            
            case "SPEED":
                if (message.toLowerCase().contains("excessive") || message.toLowerCase().contains("dangerous")) {
                    return "HIGH";
                } else {
                    return "MEDIUM";
                }
            
            case "SYSTEM":
                if (message.contains("error") || message.contains("failure")) {
                    return "HIGH";
                } else {
                    return "LOW";
                }
            
            default:
                return "LOW";
        }
    }
    
    // Additional utility methods
    
    /**
     * Get recent alerts for a device (last 24 hours)
     */
    public Page<Alert> getRecentAlertsForDevice(String deviceId, Pageable pageable) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return alertRepository.findByDeviceIdAndCreatedAtBetween(deviceId, since, LocalDateTime.now(), pageable);
    }
    
    /**
     * Get alert statistics
     */
    public AlertStatistics getAlertStatistics() {
        long total = alertRepository.count();
        long critical = alertRepository.countBySeverity("CRITICAL");
        long high = alertRepository.countBySeverity("HIGH");
        long medium = alertRepository.countBySeverity("MEDIUM");
        long low = alertRepository.countBySeverity("LOW");
        
        return new AlertStatistics(total, critical, high, medium, low);
    }
    
    /**
     * Simple statistics DTO
     */
    public static class AlertStatistics {
        private final long total;
        private final long critical;
        private final long high;
        private final long medium;
        private final long low;
        
        public AlertStatistics(long total, long critical, long high, long medium, long low) {
            this.total = total;
            this.critical = critical;
            this.high = high;
            this.medium = medium;
            this.low = low;
        }
        
        public long getTotal() { return total; }
        public long getCritical() { return critical; }
        public long getHigh() { return high; }
        public long getMedium() { return medium; }
        public long getLow() { return low; }
    }

    /**
     * Get all alerts with pagination
     */
    public Page<Alert> getAllAlerts(Pageable pageable) {
        logger.debug("Fetching all alerts with pagination");
        return alertRepository.findAll(pageable);
    }

    /**
     * Get alert by ID
     */
    public Optional<Alert> getAlertById(Long id) {
        logger.debug("Fetching alert by ID: {}", id);
        return alertRepository.findById(id);
    }
} 