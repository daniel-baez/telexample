package cl.baezdaniel.telexample.dto;

import java.time.LocalDateTime;

public class AlertFilterRequest {
    
    private String deviceId;
    private String alertType;
    private String severity;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    // Constructors
    public AlertFilterRequest() {}
    
    public AlertFilterRequest(String deviceId, String alertType, String severity, 
                             LocalDateTime startDate, LocalDateTime endDate) {
        this.deviceId = deviceId;
        this.alertType = alertType;
        this.severity = severity;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    // Getters and Setters
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
    
    public LocalDateTime getStartDate() {
        return startDate;
    }
    
    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }
    
    public LocalDateTime getEndDate() {
        return endDate;
    }
    
    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }
    
    @Override
    public String toString() {
        return "AlertFilterRequest{" +
                "deviceId='" + deviceId + '\'' +
                ", alertType='" + alertType + '\'' +
                ", severity='" + severity + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
} 