package cl.baezdaniel.telexample.benchmark;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * Benchmark-specific configuration that allows runtime switching
 * between queue-based and synchronous processing modes.
 */
@TestConfiguration
@TestPropertySource(locations = "classpath:application-benchmark.properties")
public class BenchmarkConfiguration {

    /**
     * Utility for managing benchmark configurations
     */
    @Bean
    @Primary
    public BenchmarkConfigurationHelper benchmarkConfigurationHelper() {
        return new BenchmarkConfigurationHelper();
    }

    /**
     * Helper class for managing benchmark configurations
     */
    public static class BenchmarkConfigurationHelper {
        
        public void enableQueueProcessing() {
            System.setProperty("telemetry.queue.enabled", "true");
            System.out.println("🚀 Switched to QUEUE-BASED processing mode");
        }
        
        public void disableQueueProcessing() {
            System.setProperty("telemetry.queue.enabled", "false");
            System.out.println("🔄 Switched to SYNCHRONOUS processing mode");
        }
        
        public boolean isQueueEnabled() {
            return "true".equalsIgnoreCase(System.getProperty("telemetry.queue.enabled"));
        }
        
        public void printCurrentMode() {
            String mode = isQueueEnabled() ? "QUEUE-BASED" : "SYNCHRONOUS";
            System.out.println("📊 Current processing mode: " + mode);
        }
        
        public void resetToDefault() {
            System.clearProperty("telemetry.queue.enabled");
            System.out.println("🔄 Reset to default configuration");
        }
    }
} 