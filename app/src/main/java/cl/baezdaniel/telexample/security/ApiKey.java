package cl.baezdaniel.telexample.security;

import java.util.Objects;

public class ApiKey {
    private final String value;
    private static final int MIN_LENGTH = 3;

    public ApiKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("API key cannot be null");
        }
        this.value = value;
    }

    public boolean isValid() {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return value.length() >= MIN_LENGTH;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ApiKey apiKey = (ApiKey) obj;
        return Objects.equals(value, apiKey.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
} 