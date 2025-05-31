package cl.baezdaniel.telexample.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterApiKeyAuthenticationFilter() {
        // RED: Test that the filter is registered as a bean
        assertTrue(applicationContext.containsBean("apiKeyAuthenticationFilter"));
        
        ApiKeyAuthenticationFilter filter = applicationContext.getBean(ApiKeyAuthenticationFilter.class);
        assertNotNull(filter);
    }

    @Test
    void shouldRegisterAuthenticationService() {
        // RED: Test that the authentication service is registered
        assertTrue(applicationContext.containsBean("authenticationService"));
        
        AuthenticationService service = applicationContext.getBean(AuthenticationService.class);
        assertNotNull(service);
    }

    @Test
    void shouldRegisterApiKeyValidator() {
        // RED: Test that the API key validator is registered
        assertTrue(applicationContext.containsBean("apiKeyValidator"));
        
        ApiKeyValidator validator = applicationContext.getBean(ApiKeyValidator.class);
        assertNotNull(validator);
    }

    @Test
    void shouldConfigureFilterRegistration() {
        // RED: Test that filter registration bean exists
        assertTrue(applicationContext.containsBean("apiKeyFilterRegistration"));
    }
} 