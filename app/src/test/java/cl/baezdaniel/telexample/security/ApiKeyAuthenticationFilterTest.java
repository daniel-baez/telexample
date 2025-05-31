package cl.baezdaniel.telexample.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationFilterTest {

    @Mock
    private AuthenticationService authenticationService;
    
    @Mock
    private FilterChain filterChain;
    
    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new ApiKeyAuthenticationFilter(authenticationService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        
        // Default: authentication is enabled
        when(authenticationService.isAuthenticationEnabled()).thenReturn(true);
    }

    @Test
    void shouldAllowRequestWithValidApiKey() throws ServletException, IOException {
        // Test filter allows valid requests
        String validApiKey = "valid-api-key-123";
        request.addHeader("Authorization", "Bearer " + validApiKey);
        
        when(authenticationService.authenticate(validApiKey))
            .thenReturn(AuthenticationResult.success(validApiKey));
        
        filter.doFilter(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockRequestWithInvalidApiKey() throws ServletException, IOException {
        // Test filter blocks invalid requests
        String invalidApiKey = "invalid-key";
        request.addHeader("Authorization", "Bearer " + invalidApiKey);
        
        when(authenticationService.authenticate(invalidApiKey))
            .thenReturn(AuthenticationResult.failure("Invalid API key"));
        
        filter.doFilter(request, response, filterChain);
        
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldBlockRequestWithMissingApiKey() throws ServletException, IOException {
        // Test filter blocks requests without API key
        // No Authorization header set
        
        when(authenticationService.authenticate(null))
            .thenReturn(AuthenticationResult.failure("API key is required"));
        
        filter.doFilter(request, response, filterChain);
        
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldBypassAuthenticationWhenDisabled() throws ServletException, IOException {
        // Test backward compatibility
        when(authenticationService.isAuthenticationEnabled()).thenReturn(false);
        
        filter.doFilter(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(authenticationService, never()).authenticate(any());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldExtractApiKeyFromAuthorizationHeader() throws ServletException, IOException {
        // Test API key extraction from header
        String apiKey = "test-key-456";
        request.addHeader("Authorization", "Bearer " + apiKey);
        
        when(authenticationService.authenticate(apiKey))
            .thenReturn(AuthenticationResult.success(apiKey));
        
        filter.doFilter(request, response, filterChain);
        
        verify(authenticationService).authenticate(apiKey);
    }

    @Test
    void shouldHandleInvalidAuthorizationHeaderFormat() throws ServletException, IOException {
        // Test invalid header format handling
        request.addHeader("Authorization", "InvalidFormat");
        
        when(authenticationService.authenticate(null))
            .thenReturn(AuthenticationResult.failure("API key is required"));
        
        filter.doFilter(request, response, filterChain);
        
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
    }
} 