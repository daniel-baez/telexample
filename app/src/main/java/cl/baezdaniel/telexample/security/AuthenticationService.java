package cl.baezdaniel.telexample.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final ApiKeyValidator apiKeyValidator;
    private final boolean authenticationEnabled;

    public AuthenticationService(ApiKeyValidator apiKeyValidator,
                                @Value("${endpoint.auth.enabled:false}") boolean authenticationEnabled) {
        this.apiKeyValidator = apiKeyValidator;
        this.authenticationEnabled = authenticationEnabled;
    }

    public AuthenticationResult authenticate(String apiKey) {
        if (!authenticationEnabled) {
            return AuthenticationResult.success(apiKey);
        }

        if (apiKey == null) {
            return AuthenticationResult.failure("API key is required");
        }

        if (apiKeyValidator.isValid(apiKey)) {
            return AuthenticationResult.success(apiKey);
        } else {
            return AuthenticationResult.failure("Invalid API key");
        }
    }

    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }
} 