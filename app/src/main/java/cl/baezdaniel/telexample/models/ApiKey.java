package cl.baezdaniel.telexample.models;

import java.util.Objects;

/**
 * API Key model for authentication
 */
public class ApiKey {
    
    private static final int MINIMUM_KEY_LENGTH = 8;
    
    private final String key;
    private final String description;
    
    public ApiKey(String key, String description) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        
        if (key.length() < MINIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException("API key must be at least " + MINIMUM_KEY_LENGTH + " characters long");
        }
        
        this.key = key.trim();
        this.description = description;
    }
    
    public String getKey() {
        return key;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isValid() {
        return key != null && !key.trim().isEmpty() && key.length() >= MINIMUM_KEY_LENGTH;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ApiKey apiKey = (ApiKey) obj;
        return Objects.equals(key, apiKey.key);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
    
    @Override
    public String toString() {
        return "ApiKey{" +
                "key='" + key + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
} 