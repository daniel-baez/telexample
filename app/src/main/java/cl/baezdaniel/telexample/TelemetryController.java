package cl.baezdaniel.telexample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@Validated
public class TelemetryController {
    
    private static final Logger logger = LoggerFactory.getLogger(TelemetryController.class);
    
    @Autowired
    private TelemetryRepository telemetryRepository;
    
    @PostMapping("/telemetry")
    public ResponseEntity<Map<String, Object>> createTelemetry(@Valid @RequestBody Telemetry telemetry) {
        logger.info("Creating telemetry for device: {}", telemetry.getDeviceId());
        try {
            Telemetry savedTelemetry = telemetryRepository.save(telemetry);
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedTelemetry.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping("/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<Telemetry> getLatestTelemetry(@PathVariable String deviceId) {
        logger.info("Getting latest telemetry for device: {}", deviceId);
        Optional<Telemetry> latestTelemetry = telemetryRepository.findLatestByDeviceId(deviceId);
        
        if (latestTelemetry.isPresent()) {
            return ResponseEntity.ok(latestTelemetry.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
} 