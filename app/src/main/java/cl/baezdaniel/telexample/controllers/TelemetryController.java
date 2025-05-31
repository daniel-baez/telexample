package cl.baezdaniel.telexample.controllers;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.events.TelemetryEvent;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Validated
public class TelemetryController {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryController.class);

    private final TelemetryRepository telemetryRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    // Metrics
    private final Counter telemetryEventsCounter;
    private final Counter telemetryEventsPublishedCounter;
    private final Timer telemetryProcessingTimer;

    @Autowired
    public TelemetryController(TelemetryRepository telemetryRepository, 
                              ApplicationEventPublisher eventPublisher,
                              Counter telemetryEventsCounter,
                              Counter telemetryEventsPublishedCounter,
                              Timer telemetryProcessingTimer) {
        this.telemetryRepository = telemetryRepository;
        this.eventPublisher = eventPublisher;
        this.telemetryEventsCounter = telemetryEventsCounter;
        this.telemetryEventsPublishedCounter = telemetryEventsPublishedCounter;
        this.telemetryProcessingTimer = telemetryProcessingTimer;
    }

    // BACKWARD COMPATIBILITY: Original unprotected endpoint
    @PostMapping("/telemetry")
    public ResponseEntity<Map<String, Object>> createTelemetryLegacy(@Valid @RequestBody Telemetry telemetry) {
        return createTelemetryInternal(telemetry);
    }

    // NEW PROTECTED API: Requires authentication
    @PostMapping("/api/telemetry")
    public ResponseEntity<Map<String, Object>> createTelemetry(@Valid @RequestBody Telemetry telemetry) {
        return createTelemetryInternal(telemetry);
    }

    // Shared implementation for both endpoints
    private ResponseEntity<Map<String, Object>> createTelemetryInternal(@Valid Telemetry telemetry) {
        Timer.Sample sample = Timer.start();
        
        try {
            // Increment counter for received events
            telemetryEventsCounter.increment();
            
            logger.info("Creating telemetry for device: {}", telemetry.getDeviceId());
            
            Telemetry savedTelemetry = telemetryRepository.save(telemetry);
            System.out.println("savedTelemetry: " + savedTelemetry);
            
            // 🚀 Publish event for async processing
            TelemetryEvent event = new TelemetryEvent(this, savedTelemetry);
            eventPublisher.publishEvent(event);
            
            // Increment counter for published events
            telemetryEventsPublishedCounter.increment();
            
            logger.info("📡 Published telemetry event for device: {}", savedTelemetry.getDeviceId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedTelemetry.getId());
            response.put("deviceId", savedTelemetry.getDeviceId());
            response.put("message", "Telemetry data saved successfully");
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            logger.error("Error processing telemetry for device {}: {}", telemetry.getDeviceId(), e.getMessage(), e);
            throw e; // Re-throw to let Spring handle the error response
        } finally {
            // Record processing time
            sample.stop(telemetryProcessingTimer);
        }
    }

    @GetMapping("/api/telemetry/query")
    public ResponseEntity<List<Telemetry>> queryTelemetry(@RequestParam String deviceId) {
        logger.info("Querying telemetry for device: {}", deviceId);
        
        List<Telemetry> telemetryData = telemetryRepository.findByDeviceIdOrderByTimestampDesc(deviceId);
        
        return ResponseEntity.ok(telemetryData);
    }

    @GetMapping("/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<Telemetry> getLatestTelemetry(@PathVariable String deviceId) {
        logger.info("Getting latest telemetry for device: {}", deviceId);
        
        Optional<Telemetry> latestTelemetry = telemetryRepository.findLatestByDeviceId(deviceId);
        
        if (latestTelemetry.isPresent()) {
            return ResponseEntity.ok(latestTelemetry.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
} 