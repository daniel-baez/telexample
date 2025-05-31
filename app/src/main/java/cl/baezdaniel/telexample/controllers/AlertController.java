package cl.baezdaniel.telexample.controllers;

import cl.baezdaniel.telexample.entities.Alert;
import cl.baezdaniel.telexample.services.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for Alert management API endpoints
 * Provides paginated access to alerts with advanced filtering and sorting
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    @Autowired
    private AlertService alertService;

    @Value("${alert.api.default-page-size:20}")
    private int defaultPageSize;

    @Value("${alert.api.max-page-size:100}")
    private int maxPageSize;

    /**
     * Validate pagination parameters to prevent invalid requests
     */
    private void validatePaginationParameters(int page, Integer size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size != null && size < 1) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        if (size != null && size > maxPageSize) {
            throw new IllegalArgumentException("Page size must not exceed " + maxPageSize);
        }
    }

    /**
     * Get paginated alerts for a specific device with filtering capabilities
     * 
     * GET /api/alerts/{deviceId}?page=0&size=20&sort=createdAt,desc&alertType=ANOMALY&severity=HIGH&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59
     */
    @GetMapping("/{deviceId}")
    public ResponseEntity<Page<Alert>> getAlertsForDevice(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        logger.info("Getting alerts for device: {} with filters - alertType: {}, severity: {}, page: {}, size: {}", 
                   deviceId, alertType, severity, page, size);

        try {
            // Validate pagination parameters
            validatePaginationParameters(page, size);
            
            // Validate and set page size
            int pageSize = (size != null) ? Math.min(size, maxPageSize) : defaultPageSize;
            
            // Parse sort parameter
            Sort sorting = parseSort(sort);
            Pageable pageable = PageRequest.of(page, pageSize, sorting);

            Page<Alert> alerts;
            
            // If filters are provided, use filtered query
            if (alertType != null || severity != null || startDate != null || endDate != null) {
                alerts = alertService.getAlertsWithFilters(deviceId, alertType, severity, startDate, endDate, pageable);
            } else {
                // Simple device-based query
                alerts = alertService.getAlertsForDevice(deviceId, pageable);
            }

            logger.info("Found {} alerts for device {} (page {}/{})", 
                       alerts.getNumberOfElements(), deviceId, alerts.getNumber() + 1, alerts.getTotalPages());

            return ResponseEntity.ok(alerts);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid pagination parameters for device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get alerts across all devices (admin view) with advanced filtering and pagination
     * 
     * GET /api/alerts?page=0&size=20&sort=createdAt,desc&deviceId=device-123&alertType=ANOMALY&severity=HIGH
     */
    @GetMapping
    public ResponseEntity<Page<Alert>> getAllAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        logger.info("Getting all alerts with filters - deviceId: {}, alertType: {}, severity: {}, page: {}, size: {}", 
                   deviceId, alertType, severity, page, size);

        try {
            // Validate pagination parameters
            validatePaginationParameters(page, size);
            
            // Validate and set page size
            int pageSize = (size != null) ? Math.min(size, maxPageSize) : defaultPageSize;
            
            // Parse sort parameter
            Sort sorting = parseSort(sort);
            Pageable pageable = PageRequest.of(page, pageSize, sorting);

            // Use filtered query with all parameters
            Page<Alert> alerts = alertService.getAlertsWithFilters(deviceId, alertType, severity, startDate, endDate, pageable);

            logger.info("Found {} alerts total (page {}/{})", 
                       alerts.getNumberOfElements(), alerts.getNumber() + 1, alerts.getTotalPages());

            return ResponseEntity.ok(alerts);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid pagination parameters: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent alerts for a specific device (last 24 hours)
     * 
     * GET /api/alerts/{deviceId}/recent
     */
    @GetMapping("/{deviceId}/recent")
    public ResponseEntity<Page<Alert>> getRecentAlertsForDevice(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        logger.info("Getting recent alerts for device: {}", deviceId);

        // Validate and set page size
        int pageSize = (size != null) ? Math.min(size, maxPageSize) : defaultPageSize;
        
        // Parse sort parameter
        Sort sorting = parseSort(sort);
        Pageable pageable = PageRequest.of(page, pageSize, sorting);

        Page<Alert> alerts = alertService.getRecentAlertsForDevice(deviceId, pageable);

        logger.info("Found {} recent alerts for device {}", alerts.getNumberOfElements(), deviceId);

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get alert statistics (counts by severity)
     * 
     * GET /api/alerts/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getAlertStatistics() {
        logger.info("Getting alert statistics");

        AlertService.AlertStatistics stats = alertService.getAlertStatistics();

        Map<String, Object> response = new HashMap<>();
        response.put("total", stats.getTotal());
        response.put("bySeverity", Map.of(
            "CRITICAL", stats.getCritical(),
            "HIGH", stats.getHigh(),
            "MEDIUM", stats.getMedium(),
            "LOW", stats.getLow()
        ));

        logger.info("Alert statistics: {} total alerts", stats.getTotal());

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for the Alert system
     * 
     * GET /api/alerts/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("service", "AlertController");
        health.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(health);
    }

    /**
     * Parse sort parameter string into Sort object
     * Supports format: "field,direction" or just "field" (defaults to ASC)
     * Examples: "createdAt,desc", "severity,asc", "deviceId"
     */
    private Sort parseSort(String sortString) {
        String[] parts = sortString.split(",");
        String field = parts[0];
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1])) 
                                  ? Sort.Direction.DESC 
                                  : Sort.Direction.ASC;
        
        return Sort.by(direction, field);
    }
} 