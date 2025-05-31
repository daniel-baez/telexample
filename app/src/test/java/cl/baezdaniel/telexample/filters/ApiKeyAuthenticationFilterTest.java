package cl.baezdaniel.telexample.filters;

import cl.baezdaniel.telexample.services.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiKeyAuthenticationFilter
 * Following TDD approach - these tests should fail initially
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(authenticationService);
    }

    @Test
    void shouldAllowRequestWithValidApiKey() throws ServletException, IOException {
        // Test filter allows valid requests
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer valid-api-key-123");
        request.setRequestURI("/telemetry");
        
        when(authenticationService.authenticateFromHeader("Bearer valid-api-key-123")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        verify(authenticationService).authenticateFromHeader("Bearer valid-api-key-123");
    }

    @Test
    void shouldBlockRequestWithInvalidApiKey() throws ServletException, IOException {
        // Test filter blocks invalid requests
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer invalid-key");
        request.setRequestURI("/telemetry");
        
        when(authenticationService.authenticateFromHeader("Bearer invalid-key")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verifyNoInteractions(filterChain);
        verify(authenticationService).authenticateFromHeader("Bearer invalid-key");
    }

    @Test
    void shouldBlockRequestWithMissingApiKey() throws ServletException, IOException {
        // Test filter blocks requests without Authorization header
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/telemetry");
        
        when(authenticationService.authenticateFromHeader(null)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verifyNoInteractions(filterChain);
        verify(authenticationService).authenticateFromHeader(null);
    }

    @Test
    void shouldBypassAuthenticationWhenDisabled() throws ServletException, IOException {
        // Test backward compatibility - when auth service allows bypass
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/telemetry");
        // No Authorization header
        
        when(authenticationService.authenticateFromHeader(null)).thenReturn(true); // Auth disabled

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        verify(authenticationService).authenticateFromHeader(null);
    }

    @Test
    void shouldAllowHealthCheckWithoutAuth() throws ServletException, IOException {
        // Test health endpoints remain accessible
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/actuator/health");
        // No Authorization header

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authenticationService); // Auth service should not be called
    }

    @Test
    void shouldAllowActuatorEndpointsWithoutAuth() throws ServletException, IOException {
        // Test actuator endpoints remain accessible
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/actuator/info");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authenticationService);
    }

    @Test
    void shouldProtectTelemetryEndpoints() throws ServletException, IOException {
        // Test telemetry endpoints require authentication
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/telemetry");
        
        when(authenticationService.authenticateFromHeader(null)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldProtectAlertEndpoints() throws ServletException, IOException {
        // Test alert endpoints require authentication
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/api/alerts");
        
        when(authenticationService.authenticateFromHeader(null)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturnJsonErrorResponse() throws ServletException, IOException {
        // Test error response format
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/telemetry");
        
        when(authenticationService.authenticateFromHeader(null)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Unauthorized"));
        assertTrue(response.getContentAsString().contains("Valid API key required"));
    }
} 