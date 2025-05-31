package cl.baezdaniel.telexample.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApiKeyValidator {
    private static final int MIN_LENGTH = 3;
    private static final String VALID_PATTERN = "^[a-zA-Z0-9._-]+$";
    
    private final Set<String> validApiKeys;

    public ApiKeyValidator(@Value("${telemetry.security.api-keys:valid-test-key,another-test-key,prod-key-12345}") String apiKeysConfig) {
        this.validApiKeys = Arrays.stream(apiKeysConfig.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toSet());
    }

    public boolean isValid(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        if (apiKey.length() < MIN_LENGTH) {
            return false;
        }
        
        if (!apiKey.matches(VALID_PATTERN)) {
            return false;
        }
        
        // Check against configured API keys
        return validApiKeys.contains(apiKey);
    }
} 