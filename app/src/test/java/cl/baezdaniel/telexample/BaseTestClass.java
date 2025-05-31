package cl.baezdaniel.telexample;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseTestClass {
    // Base test class for common test configuration
} 