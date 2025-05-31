package cl.baezdaniel.telexample.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiKey model
 * Following TDD approach - these tests should fail initially
 */
class ApiKeyTest {

    @Test
    void shouldValidateValidApiKey() {
        // Test valid API key format and authentication
        String validKey = "valid-api-key-123";
        ApiKey apiKey = new ApiKey(validKey, "Test key");
        
        assertTrue(apiKey.isValid());
        assertEquals(validKey, apiKey.getKey());
        assertEquals("Test key", apiKey.getDescription());
    }

    @Test
    void shouldRejectInvalidApiKey() {
        // Test invalid API key rejection - null key
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiKey(null, "Invalid key");
        });
        
        // Test invalid API key rejection - empty key
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiKey("", "Invalid key");
        });
        
        // Test invalid API key rejection - blank key
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiKey("   ", "Invalid key");
        });
    }

    @Test
    void shouldRejectMissingApiKey() {
        // Test missing API key handling
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiKey(null, "Missing key");
        });
    }

    @Test
    void shouldValidateApiKeyEquality() {
        // Test API key equality
        ApiKey key1 = new ApiKey("same-key", "Description 1");
        ApiKey key2 = new ApiKey("same-key", "Description 2");
        ApiKey key3 = new ApiKey("different-key", "Description 3");
        
        assertEquals(key1, key2); // Same key value
        assertNotEquals(key1, key3); // Different key value
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void shouldValidateMinimumKeyLength() {
        // Test minimum key length requirement
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiKey("short", "Too short key");
        });
        
        // Valid key with minimum length
        ApiKey validKey = new ApiKey("minimum-length-key", "Valid key");
        assertTrue(validKey.isValid());
    }
} 