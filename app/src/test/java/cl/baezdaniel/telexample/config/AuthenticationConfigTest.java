package cl.baezdaniel.telexample.config;

import cl.baezdaniel.telexample.models.ApiKey;
import cl.baezdaniel.telexample.services.ApiKeyValidator;
import cl.baezdaniel.telexample.services.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Authentication Configuration
 * Following TDD approach - these tests should fail initially
 */
@SpringBootTest
class AuthenticationConfigTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ApiKeyValidator apiKeyValidator;

    @Test
    void shouldLoadDefaultConfiguration() {
        // Test default authentication behavior
        assertNotNull(authenticationService);
        assertNotNull(apiKeyValidator);
    }

    @SpringBootTest
    @TestPropertySource(properties = {
        "endpoint.auth.enabled=false"
    })
    static class AuthenticationDisabledTest {
        
        @Autowired
        private AuthenticationService authenticationService;
        
        @Test
        void shouldDisableAuthenticationWhenConfigured() {
            // Test backward compatibility mode - authentication disabled
            // Test that authentication always returns true when disabled
            assertTrue(authenticationService.authenticate("any-key"));
            assertTrue(authenticationService.authenticate(null));
        }
    }

    @SpringBootTest
    @TestPropertySource(properties = {
        "endpoint.auth.enabled=true",
        "endpoint.auth.api-keys=test-key-123,production-key-456"
    })
    static class AuthenticationEnabledTest {
        
        @Autowired
        private ApiKeyValidator apiKeyValidator;
        
        @Autowired
        private AuthenticationService authenticationService;
        
        @Test
        void shouldLoadApiKeysFromConfiguration() {
            // Test API key configuration loading
            assertTrue(apiKeyValidator.isValid("test-key-123"));
            assertTrue(apiKeyValidator.isValid("production-key-456"));
            assertFalse(apiKeyValidator.isValid("invalid-key"));
        }
        
        @Test
        void shouldEnableAuthenticationByDefault() {
            // Test default authentication behavior when enabled
            assertFalse(authenticationService.authenticate("invalid-key"));
        }
    }

    @SpringBootTest
    @TestPropertySource(properties = {
        "endpoint.auth.enabled=true",
        "endpoint.auth.api-keys="
    })
    static class EmptyApiKeysTest {
        
        @Autowired
        private ApiKeyValidator apiKeyValidator;
        
        @Test
        void shouldHandleEmptyApiKeysList() {
            // Test handling of empty API keys configuration
            assertFalse(apiKeyValidator.isValid("any-key"));
        }
    }
} 