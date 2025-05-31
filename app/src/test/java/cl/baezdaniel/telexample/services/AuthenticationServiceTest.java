package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.models.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService
 * Following TDD approach - these tests should fail initially
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private ApiKeyValidator apiKeyValidator;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(apiKeyValidator, true); // auth enabled
    }

    @Test
    void shouldAuthenticateWithValidApiKey() {
        // Test successful authentication with valid API key
        String validKey = "valid-api-key-123";
        
        when(apiKeyValidator.isValid(validKey)).thenReturn(true);

        boolean result = authenticationService.authenticate(validKey);

        assertTrue(result);
        verify(apiKeyValidator).isValid(validKey);
    }

    @Test
    void shouldReturnUnauthorizedForInvalidKey() {
        // Test authentication failure with invalid key
        String invalidKey = "invalid-key";
        
        when(apiKeyValidator.isValid(invalidKey)).thenReturn(false);

        boolean result = authenticationService.authenticate(invalidKey);

        assertFalse(result);
        verify(apiKeyValidator).isValid(invalidKey);
    }

    @Test
    void shouldReturnUnauthorizedForNullKey() {
        // Test authentication failure with null key
        when(apiKeyValidator.isValid(null)).thenReturn(false);

        boolean result = authenticationService.authenticate(null);

        assertFalse(result);
        verify(apiKeyValidator).isValid(null);
    }

    @Test
    void shouldAllowBypassWhenAuthenticationDisabled() {
        // Test backward compatibility mode - authentication disabled
        AuthenticationService disabledAuthService = new AuthenticationService(apiKeyValidator, false);

        boolean result = disabledAuthService.authenticate("any-key");

        assertTrue(result); // Should always return true when auth is disabled
        verifyNoInteractions(apiKeyValidator); // Validator should not be called
    }

    @Test
    void shouldAllowBypassWithNullKeyWhenAuthenticationDisabled() {
        // Test that even null keys are accepted when auth is disabled
        AuthenticationService disabledAuthService = new AuthenticationService(apiKeyValidator, false);

        boolean result = disabledAuthService.authenticate(null);

        assertTrue(result);
        verifyNoInteractions(apiKeyValidator);
    }

    @Test
    void shouldExtractKeyFromBearerToken() {
        // Test extracting API key from Authorization header
        String bearerToken = "Bearer test-api-key-123";
        String expectedKey = "test-api-key-123";
        
        when(apiKeyValidator.isValid(expectedKey)).thenReturn(true);

        boolean result = authenticationService.authenticateFromHeader(bearerToken);

        assertTrue(result);
        verify(apiKeyValidator).isValid(expectedKey);
    }

    @Test
    void shouldRejectInvalidBearerTokenFormat() {
        // Test rejection of invalid bearer token format
        String invalidToken = "InvalidFormat test-key";

        boolean result = authenticationService.authenticateFromHeader(invalidToken);

        assertFalse(result);
        verifyNoInteractions(apiKeyValidator);
    }

    @Test
    void shouldRejectMissingBearerPrefix() {
        // Test rejection when Bearer prefix is missing
        String tokenWithoutBearer = "test-api-key-123";

        boolean result = authenticationService.authenticateFromHeader(tokenWithoutBearer);

        assertFalse(result);
        verifyNoInteractions(apiKeyValidator);
    }

    @Test
    void shouldHandleEmptyBearerToken() {
        // Test handling of empty bearer token
        String emptyToken = "Bearer ";
        
        when(apiKeyValidator.isValid("")).thenReturn(false);

        boolean result = authenticationService.authenticateFromHeader(emptyToken);

        assertFalse(result);
        verify(apiKeyValidator).isValid(""); // The service extracts empty string and validates it
    }

    @Test
    void shouldReturnApiKeyDetailsForValidKey() {
        // Test retrieving API key details for valid key
        String validKey = "valid-api-key-123";
        ApiKey expectedApiKey = new ApiKey(validKey, "Test key");
        
        when(apiKeyValidator.findByKey(validKey)).thenReturn(expectedApiKey);

        ApiKey result = authenticationService.getApiKeyDetails(validKey);

        assertNotNull(result);
        assertEquals(validKey, result.getKey());
        assertEquals("Test key", result.getDescription());
        verify(apiKeyValidator).findByKey(validKey);
    }

    @Test
    void shouldReturnNullForInvalidKey() {
        // Test retrieving API key details for invalid key
        String invalidKey = "invalid-key";
        
        when(apiKeyValidator.findByKey(invalidKey)).thenReturn(null);

        ApiKey result = authenticationService.getApiKeyDetails(invalidKey);

        assertNull(result);
        verify(apiKeyValidator).findByKey(invalidKey);
    }
} 