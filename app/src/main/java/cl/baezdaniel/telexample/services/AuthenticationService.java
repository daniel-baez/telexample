package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.models.ApiKey;

/**
 * Service for handling API key authentication
 */
public class AuthenticationService {
    
    private final ApiKeyValidator apiKeyValidator;
    private final boolean authenticationEnabled;
    
    public AuthenticationService(ApiKeyValidator apiKeyValidator, boolean authenticationEnabled) {
        this.apiKeyValidator = apiKeyValidator;
        this.authenticationEnabled = authenticationEnabled;
    }
    
    public boolean authenticate(String apiKey) {
        // If authentication is disabled, always allow access (backward compatibility)
        if (!authenticationEnabled) {
            return true;
        }
        
        return apiKeyValidator.isValid(apiKey);
    }
    
    public boolean authenticateFromHeader(String authorizationHeader) {
        // If authentication is disabled, always allow access (backward compatibility)
        if (!authenticationEnabled) {
            return true;
        }
        
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String apiKey = authorizationHeader.substring(7); // Remove "Bearer " prefix
        return apiKeyValidator.isValid(apiKey);
    }
    
    public ApiKey getApiKeyDetails(String apiKey) {
        return apiKeyValidator.findByKey(apiKey);
    }
    
    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }
} 