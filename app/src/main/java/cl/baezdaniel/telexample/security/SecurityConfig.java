package cl.baezdaniel.telexample.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Bean
    public AuthenticationService authenticationService(ApiKeyValidator apiKeyValidator,
                                                      @Value("${endpoint.auth.enabled:#{null}}") Boolean endpointAuthEnabled,
                                                      @Value("${telemetry.security.enabled:false}") boolean telemetrySecurityEnabled) {
        // endpoint.auth.enabled takes precedence if explicitly set, otherwise use telemetry.security.enabled
        boolean authenticationEnabled = (endpointAuthEnabled != null) ? endpointAuthEnabled : telemetrySecurityEnabled;
        return new AuthenticationService(apiKeyValidator, authenticationEnabled);
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilterRegistration(
            ApiKeyAuthenticationFilter filter) {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*"); // Apply to all API endpoints
        registration.setOrder(1); // High priority
        return registration;
    }
} 