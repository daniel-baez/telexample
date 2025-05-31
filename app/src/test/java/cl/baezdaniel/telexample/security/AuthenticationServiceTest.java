package cl.baezdaniel.telexample.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {

    @Mock
    private ApiKeyValidator apiKeyValidator;
    
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Create service with authentication enabled by default for tests
        authenticationService = new AuthenticationService(apiKeyValidator, true);
    }

    @Test
    void shouldAuthenticateWithValidApiKey() {
        // Test successful authentication
        String validApiKey = "valid-api-key-123";
        when(apiKeyValidator.isValid(validApiKey)).thenReturn(true);
        
        AuthenticationResult result = authenticationService.authenticate(validApiKey);
        
        assertTrue(result.isAuthenticated());
        assertEquals(validApiKey, result.getApiKey());
        assertNull(result.getErrorMessage());
    }

    @Test
    void shouldReturnUnauthorizedForInvalidKey() {
        // Test authentication failure
        String invalidApiKey = "invalid-key";
        when(apiKeyValidator.isValid(invalidApiKey)).thenReturn(false);
        
        AuthenticationResult result = authenticationService.authenticate(invalidApiKey);
        
        assertFalse(result.isAuthenticated());
        assertNull(result.getApiKey());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void shouldReturnUnauthorizedForNullKey() {
        // Test null key handling
        AuthenticationResult result = authenticationService.authenticate(null);
        
        assertFalse(result.isAuthenticated());
        assertNull(result.getApiKey());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void shouldAllowBypassWhenAuthenticationDisabled() {
        // Test backward compatibility mode
        authenticationService = new AuthenticationService(apiKeyValidator, false); // disabled
        
        AuthenticationResult result = authenticationService.authenticate("any-key");
        
        assertTrue(result.isAuthenticated());
        assertEquals("any-key", result.getApiKey());
        assertNull(result.getErrorMessage());
        
        // Should not call validator when disabled
        verifyNoInteractions(apiKeyValidator);
    }

    @Test
    void shouldCheckAuthenticationStatus() {
        // Test authentication status checking
        assertTrue(authenticationService.isAuthenticationEnabled());
        
        AuthenticationService disabledService = new AuthenticationService(apiKeyValidator, false);
        assertFalse(disabledService.isAuthenticationEnabled());
    }
} 