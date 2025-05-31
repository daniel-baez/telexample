package cl.baezdaniel.telexample.security;

public class AuthenticationResult {
    private final boolean authenticated;
    private final String apiKey;
    private final String errorMessage;

    private AuthenticationResult(boolean authenticated, String apiKey, String errorMessage) {
        this.authenticated = authenticated;
        this.apiKey = apiKey;
        this.errorMessage = errorMessage;
    }

    public static AuthenticationResult success(String apiKey) {
        return new AuthenticationResult(true, apiKey, null);
    }

    public static AuthenticationResult failure(String errorMessage) {
        return new AuthenticationResult(false, null, errorMessage);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
} 