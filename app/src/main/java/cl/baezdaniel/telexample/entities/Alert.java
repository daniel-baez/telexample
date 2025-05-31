package cl.baezdaniel.telexample.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_device_id", columnList = "deviceId"),
    @Index(name = "idx_alert_type", columnList = "alertType"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_severity", columnList = "severity"),
    @Index(name = "idx_fingerprint", columnList = "fingerprint")
})
public class Alert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Device ID cannot be blank")
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    
    @NotBlank(message = "Alert type cannot be blank")
    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;
    
    @NotBlank(message = "Severity cannot be blank")
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;
    
    @NotBlank(message = "Message cannot be blank")
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @NotNull(message = "Created date cannot be null")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // Context Fields
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    @Column(name = "processor_name", length = 100)
    private String processorName;
    
    // Deduplication Fields
    @Column(name = "fingerprint", length = 32, unique = true)
    private String fingerprint;
    
    // Business Fields (JSON for flexibility)
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    // Constructors
    public Alert() {}
    
    public Alert(String deviceId, String alertType, String severity, String message, 
                Double latitude, Double longitude, String processorName, String fingerprint, String metadata) {
        this.deviceId = deviceId;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.latitude = latitude;
        this.longitude = longitude;
        this.processorName = processorName;
        this.fingerprint = fingerprint;
        this.metadata = metadata;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public String getAlertType() {
        return alertType;
    }
    
    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }
    
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public String getProcessorName() {
        return processorName;
    }
    
    public void setProcessorName(String processorName) {
        this.processorName = processorName;
    }
    
    public String getFingerprint() {
        return fingerprint;
    }
    
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    @Override
    public String toString() {
        return "Alert{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", alertType='" + alertType + '\'' +
                ", severity='" + severity + '\'' +
                ", message='" + message + '\'' +
                ", createdAt=" + createdAt +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", processorName='" + processorName + '\'' +
                '}';
    }
} 