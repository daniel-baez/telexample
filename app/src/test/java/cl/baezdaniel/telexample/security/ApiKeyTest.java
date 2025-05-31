package cl.baezdaniel.telexample.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyTest {

    @Test
    void shouldValidateValidApiKey() {
        // RED: This test will fail because ApiKey class doesn't exist yet
        String validKey = "test-api-key-123";
        ApiKey apiKey = new ApiKey(validKey);
        
        assertTrue(apiKey.isValid());
        assertEquals(validKey, apiKey.getValue());
    }

    @Test
    void shouldRejectInvalidApiKey() {
        // RED: Test invalid API key rejection
        ApiKey emptyKey = new ApiKey("");
        ApiKey tooShortKey = new ApiKey("ab");
        
        assertFalse(emptyKey.isValid());
        assertFalse(tooShortKey.isValid());
    }

    @Test
    void shouldRejectMissingApiKey() {
        // RED: Test missing API key handling
        assertThrows(IllegalArgumentException.class, () -> {
            new ApiKey(null);
        });
    }

    @Test
    void shouldHandleApiKeyEquality() {
        // RED: Test API key equality
        ApiKey key1 = new ApiKey("same-key");
        ApiKey key2 = new ApiKey("same-key");
        ApiKey key3 = new ApiKey("different-key");
        
        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertEquals(key1.hashCode(), key2.hashCode());
    }
} 