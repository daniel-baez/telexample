package cl.baezdaniel.telexample.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiKeyAuthenticationFilter implements Filter {
    private final AuthenticationService authenticationService;

    public ApiKeyAuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Bypass authentication if disabled
        if (!authenticationService.isAuthenticationEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // Extract API key from Authorization header
        String apiKey = extractApiKey(httpRequest);
        
        // Authenticate
        AuthenticationResult result = authenticationService.authenticate(apiKey);
        
        if (result.isAuthenticated()) {
            // Allow request to proceed
            chain.doFilter(request, response);
        } else {
            // Block request with 401 Unauthorized
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"Unauthorized\",\"message\":\"" + result.getErrorMessage() + "\"}"
            );
        }
    }

    private String extractApiKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }
} 