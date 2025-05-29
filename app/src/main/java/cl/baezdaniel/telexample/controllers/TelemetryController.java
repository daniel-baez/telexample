package cl.baezdaniel.telexample.controllers;

import cl.baezdaniel.telexample.entities.Telemetry;
import cl.baezdaniel.telexample.repositories.TelemetryRepository;
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
import java.util.Map;
import java.util.Optional;

@RestController
@Validated
public class TelemetryController {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryController.class);

    @Autowired
    private TelemetryRepository telemetryRepository;

    @PostMapping("/telemetry")
    public ResponseEntity<Map<String, Object>> createTelemetry(@Valid @RequestBody Telemetry telemetry) {
        logger.info("Creating telemetry for device: {}", telemetry.getDeviceId());
        
        Telemetry savedTelemetry = telemetryRepository.save(telemetry);
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedTelemetry.getId());
        response.put("deviceId", savedTelemetry.getDeviceId());
        response.put("message", "Telemetry data saved successfully");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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