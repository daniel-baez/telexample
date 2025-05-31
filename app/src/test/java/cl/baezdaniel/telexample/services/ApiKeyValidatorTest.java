package cl.baezdaniel.telexample.services;

import cl.baezdaniel.telexample.models.ApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for ApiKeyValidator service
 * Following TDD approach - these tests should fail initially
 */
class ApiKeyValidatorTest {

    private ApiKeyValidator validator;
    private List<ApiKey> testApiKeys;

    @BeforeEach
    void setUp() {
        testApiKeys = Arrays.asList(
            new ApiKey("test-key-123", "Test key"),
            new ApiKey("production-key-456", "Production key"),
            new ApiKey("development-key-789", "Development key")
        );
        validator = new ApiKeyValidator(testApiKeys);
    }

    @Test
    void shouldValidateValidApiKey() {
        // Test successful validation with valid key
        assertTrue(validator.isValid("test-key-123"));
        assertTrue(validator.isValid("production-key-456"));
        assertTrue(validator.isValid("development-key-789"));
    }

    @Test
    void shouldRejectInvalidApiKey() {
        // Test validation failure with invalid key
        assertFalse(validator.isValid("invalid-key"));
        assertFalse(validator.isValid("wrong-key-123"));
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid(null));
    }

    @Test
    void shouldRejectMissingApiKey() {
        // Test validation failure with missing key
        assertFalse(validator.isValid(null));
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("   "));
    }

    @Test
    void shouldHandleEmptyApiKeyList() {
        // Test validator with empty API key list
        ApiKeyValidator emptyValidator = new ApiKeyValidator(Arrays.asList());
        assertFalse(emptyValidator.isValid("any-key"));
    }

    @Test
    void shouldValidateKeysCaseInsensitive() {
        // Test case insensitive validation
        assertFalse(validator.isValid("TEST-KEY-123")); // Should be case sensitive
        assertFalse(validator.isValid("Test-Key-123"));
    }

    @Test
    void shouldFindApiKeyByValue() {
        // Test finding API key by value
        ApiKey foundKey = validator.findByKey("test-key-123");
        assertNotNull(foundKey);
        assertEquals("test-key-123", foundKey.getKey());
        assertEquals("Test key", foundKey.getDescription());

        // Test not found
        ApiKey notFound = validator.findByKey("non-existent-key");
        assertNull(notFound);
    }
} 