package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.dto.AlertCreationRequest;
import cl.baezdaniel.telexample.dto.AlertFilterRequest;
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
    public Alert createAlert(AlertCreationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Alert creation request cannot be null");
        }
        
        return createAlertInternal(request);
    }
    
    @Transactional
    private Alert createAlertInternal(AlertCreationRequest request) {
        logger.debug("Creating alert for device {}: {}", request.getDeviceId(), request.getAlertType());
        
        // Generate fingerprint for deduplication
        String fingerprint = generateFingerprint(request.getDeviceId(), request.getAlertType(), 
                                               request.getLatitude(), request.getLongitude());
        
        // IMPROVED: Use synchronized block for critical section to prevent race conditions
        synchronized (this) {
            // Check for duplicates within synchronized block
            Optional<Alert> existingAlert = alertRepository.findByFingerprint(fingerprint);
            if (existingAlert.isPresent()) {
                logger.debug("Duplicate alert detected for fingerprint: {}, returning existing", fingerprint);
                return existingAlert.get();
            }
            
            // Determine severity based on alert type and context
            String severity = determineSeverity(request.getAlertType(), request.getMessage());
            
            // Create new alert
            Alert alert = new Alert(
                request.getDeviceId(),
                request.getAlertType(),
                severity,
                request.getMessage(),
                request.getLatitude(),
                request.getLongitude(),
                request.getProcessorName(),
                fingerprint,
                request.getMetadata()
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
     * Get alerts with advanced filtering
     */
    public Page<Alert> getAlertsWithFilters(AlertFilterRequest filters, Pageable pageable) {
        logger.debug("Fetching alerts with filters: {}", filters);
        return alertRepository.findWithFilters(
            filters.getDeviceId(),
            filters.getAlertType(),
            filters.getSeverity(),
            filters.getStartDate(),
            filters.getEndDate(),
            pageable
        );
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
                if (message.contains("Invalid coordinates") || message.contains("extreme")) {
                    return "HIGH";
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
     * Get alerts with filter (used by tests and controller)
     */
    public Page<Alert> getAlertsWithFilter(AlertFilterRequest filter, Pageable pageable) {
        if (filter == null) {
            return getAllAlerts(pageable);
        }

        // Use existing method with filters
        return getAlertsWithFilters(filter, pageable);
    }

    /**
     * Get alert by ID
     */
    public Optional<Alert> getAlertById(Long id) {
        logger.debug("Fetching alert by ID: {}", id);
        return alertRepository.findById(id);
    }

    /**
     * Get all alerts with pagination
     */
    public Page<Alert> getAllAlerts(Pageable pageable) {
        logger.debug("Fetching all alerts with pagination");
        return alertRepository.findAll(pageable);
    }
} 