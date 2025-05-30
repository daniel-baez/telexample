package cl.baezdaniel.telexample.dto;

import jakarta.validation.constraints.NotBlank;

public class AlertCreationRequest {
    
    @NotBlank(message = "Device ID cannot be blank")
    private String deviceId;
    
    @NotBlank(message = "Alert type cannot be blank")
    private String alertType;
    
    @NotBlank(message = "Message cannot be blank")
    private String message;
    
    private Double latitude;
    private Double longitude;
    private String processorName;
    private String metadata;
    
    // Constructors
    public AlertCreationRequest() {}
    
    public AlertCreationRequest(String deviceId, String alertType, String message, 
                               Double latitude, Double longitude, String processorName, String metadata) {
        this.deviceId = deviceId;
        this.alertType = alertType;
        this.message = message;
        this.latitude = latitude;
        this.longitude = longitude;
        this.processorName = processorName;
        this.metadata = metadata;
    }
    
    // Builder pattern
    public static AlertCreationRequestBuilder builder() {
        return new AlertCreationRequestBuilder();
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
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
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
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    // Builder class
    public static class AlertCreationRequestBuilder {
        private String deviceId;
        private String alertType;
        private String message;
        private Double latitude;
        private Double longitude;
        private String processorName;
        private String metadata;
        
        public AlertCreationRequestBuilder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }
        
        public AlertCreationRequestBuilder alertType(String alertType) {
            this.alertType = alertType;
            return this;
        }
        
        public AlertCreationRequestBuilder message(String message) {
            this.message = message;
            return this;
        }
        
        public AlertCreationRequestBuilder latitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }
        
        public AlertCreationRequestBuilder longitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }
        
        public AlertCreationRequestBuilder processorName(String processorName) {
            this.processorName = processorName;
            return this;
        }
        
        public AlertCreationRequestBuilder metadata(String metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public AlertCreationRequest build() {
            return new AlertCreationRequest(deviceId, alertType, message, latitude, longitude, processorName, metadata);
        }
    }
} 