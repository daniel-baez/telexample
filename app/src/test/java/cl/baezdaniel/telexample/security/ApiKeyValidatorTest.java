package cl.baezdaniel.telexample.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyValidatorTest {

    private ApiKeyValidator validator;

    @BeforeEach
    void setUp() {
        // Use the same test API keys as configured in tests
        validator = new ApiKeyValidator("valid-test-key,test-key-123,auth-test-key");
    }

    @Test
    void shouldValidateValidApiKey() {
        // Test valid API key validation with configured keys
        String validKey = "valid-test-key";
        
        assertTrue(validator.isValid(validKey));
    }

    @Test
    void shouldRejectInvalidApiKey() {
        // Test invalid API key rejection
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid(null));
        assertFalse(validator.isValid("ab")); // too short
        assertFalse(validator.isValid("   ")); // only whitespace
        assertFalse(validator.isValid("not-in-whitelist")); // not in whitelist
    }

    @Test
    void shouldValidateApiKeyFormat() {
        // Test API key format validation with configured keys
        assertTrue(validator.isValid("test-key-123")); // in configured keys
        assertTrue(validator.isValid("auth-test-key")); // in configured keys
        
        // Should reject keys not in configured list, even if format is valid
        assertFalse(validator.isValid("valid-format-but-not-configured"));
        assertFalse(validator.isValid("key with spaces")); // invalid format
        assertFalse(validator.isValid("key@#$%")); // invalid format
    }

    @Test
    void shouldCheckMinimumLength() {
        // Test minimum length requirement
        String shortKey = "ab";
        String validKey = "valid-test-key"; // in configured keys
        
        assertFalse(validator.isValid(shortKey));
        assertTrue(validator.isValid(validKey));
    }

    @Test
    void shouldValidateConfiguredKeys() {
        // Test all configured keys are valid
        assertTrue(validator.isValid("valid-test-key"));
        assertTrue(validator.isValid("test-key-123"));
        assertTrue(validator.isValid("auth-test-key"));
        
        // Test non-configured keys are invalid
        assertFalse(validator.isValid("unknown-key-123"));
        assertFalse(validator.isValid("random-key-456"));
    }
} 