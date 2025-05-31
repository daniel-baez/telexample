package cl.baezdaniel.telexample.filters;

import cl.baezdaniel.telexample.services.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(1) // High priority filter
public class RateLimitFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    // Endpoints to apply rate limiting
    private static final Set<String> RATE_LIMITED_ENDPOINTS = Set.of(
        "/telemetry",
        "/api/alerts"
    );
    
    private final RateLimitService rateLimitService;
    
    @Value("${rate-limit.enabled:false}")
    private boolean rateLimitEnabled;
    
    @Autowired
    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // If rate limiting is disabled, skip processing
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        // Only apply rate limiting to specific endpoints
        if (!shouldApplyRateLimit(requestURI, method)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract rate limiting key (device ID from request body or IP address)
        String rateLimitKey = extractRateLimitKey(request);
        
        // Check rate limit
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(rateLimitKey);
        
        // Add rate limit headers
        addRateLimitHeaders(response, result);
        
        if (!result.isAllowed()) {
            handleRateLimitExceeded(response, result, rateLimitKey, requestURI);
            return;
        }
        
        logger.debug("✅ Rate limit check passed for key: {} on endpoint: {}", rateLimitKey, requestURI);
        filterChain.doFilter(request, response);
    }
    
    /**
     * Determines if rate limiting should be applied to this request
     */
    private boolean shouldApplyRateLimit(String requestURI, String method) {
        // Apply to telemetry POST endpoints and alert GET endpoints
        if ("/telemetry".equals(requestURI) && "POST".equals(method)) {
            return true;
        }
        if (requestURI.startsWith("/api/alerts") && "GET".equals(method)) {
            return true;
        }
        return false;
    }
    
    /**
     * Extracts the rate limiting key from the request
     * For telemetry endpoints, try to extract device ID from request body
     * For other endpoints, use IP address
     */
    private String extractRateLimitKey(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        
        // For telemetry endpoints, try to extract device ID from parameters or path
        if (requestURI.startsWith("/telemetry")) {
            String deviceId = request.getParameter("deviceId");
            if (deviceId != null && !deviceId.trim().isEmpty()) {
                return "device:" + deviceId;
            }
        }
        
        // For alert endpoints, extract device ID from path
        if (requestURI.startsWith("/api/alerts/")) {
            String pathInfo = requestURI.substring("/api/alerts/".length());
            if (!pathInfo.isEmpty() && !pathInfo.contains("/")) {
                return "device:" + pathInfo;
            }
        }
        
        // Fallback to IP address
        String clientIP = getClientIP(request);
        return "ip:" + clientIP;
    }
    
    /**
     * Extracts the real client IP address
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Adds standard rate limiting headers to the response
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitService.RateLimitResult result) {
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + 60)); // Reset in 1 minute
        
        if (!result.isAllowed()) {
            response.setHeader("X-RateLimit-Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        }
    }
    
    /**
     * Handles rate limit exceeded scenario
     */
    private void handleRateLimitExceeded(HttpServletResponse response, RateLimitService.RateLimitResult result, 
                                       String rateLimitKey, String requestURI) throws IOException {
        
        logger.warn("🚫 Rate limit exceeded for key: {} on endpoint: {}. Reason: {}", 
            rateLimitKey, requestURI, result.getReason());
        
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        
        String jsonResponse = String.format("""
            {
                "error": "Rate limit exceeded",
                "reason": "%s",
                "retryAfter": %d,
                "remainingTokens": %d,
                "timestamp": "%s"
            }""", 
            result.getReason(),
            result.getRetryAfterSeconds(),
            result.getRemainingTokens(),
            java.time.Instant.now()
        );
        
        response.getWriter().write(jsonResponse);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestURI = request.getRequestURI();
        
        // Skip filtering for health checks, metrics, and static resources
        return requestURI.startsWith("/actuator/") ||
               requestURI.startsWith("/health") ||
               requestURI.startsWith("/static/") ||
               requestURI.startsWith("/webjars/") ||
               requestURI.startsWith("/favicon.ico");
    }
} 