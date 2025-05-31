package cl.baezdaniel.telexample.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Bean
    public AuthenticationService authenticationService(ApiKeyValidator apiKeyValidator,
                                                      @Value("${telemetry.security.enabled:false}") boolean authenticationEnabled) {
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