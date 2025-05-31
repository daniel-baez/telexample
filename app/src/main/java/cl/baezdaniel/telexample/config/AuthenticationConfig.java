package cl.baezdaniel.telexample.config;

import cl.baezdaniel.telexample.models.ApiKey;
import cl.baezdaniel.telexample.services.ApiKeyValidator;
import cl.baezdaniel.telexample.services.AuthenticationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration for API key authentication
 */
@Configuration
public class AuthenticationConfig {
    
    @Value("${endpoint.auth.enabled:true}")
    private boolean authenticationEnabled;
    
    @Value("${endpoint.auth.api-keys:}")
    private String apiKeysConfig;
    
    @Bean
    public ApiKeyValidator apiKeyValidator() {
        List<ApiKey> apiKeys = parseApiKeys(apiKeysConfig);
        return new ApiKeyValidator(apiKeys);
    }
    
    @Bean
    public AuthenticationService authenticationService(ApiKeyValidator apiKeyValidator) {
        return new AuthenticationService(apiKeyValidator, authenticationEnabled);
    }
    
    private List<ApiKey> parseApiKeys(String apiKeysConfig) {
        if (apiKeysConfig == null || apiKeysConfig.trim().isEmpty()) {
            return List.of();
        }
        
        return Arrays.stream(apiKeysConfig.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty() && key.length() >= 8) // Minimum length validation
                .map(key -> new ApiKey(key, "Configured API key"))
                .collect(Collectors.toList());
    }
} 