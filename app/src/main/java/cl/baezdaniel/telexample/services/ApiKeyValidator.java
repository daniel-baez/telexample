package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.models.ApiKey;

import java.util.List;

/**
 * Service for validating API keys
 */
public class ApiKeyValidator {
    
    private final List<ApiKey> validApiKeys;
    
    public ApiKeyValidator(List<ApiKey> validApiKeys) {
        this.validApiKeys = validApiKeys != null ? validApiKeys : List.of();
    }
    
    public boolean isValid(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        
        return validApiKeys.stream()
                .anyMatch(apiKey -> apiKey.getKey().equals(key.trim()));
    }
    
    public ApiKey findByKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        
        return validApiKeys.stream()
                .filter(apiKey -> apiKey.getKey().equals(key.trim()))
                .findFirst()
                .orElse(null);
    }
} 