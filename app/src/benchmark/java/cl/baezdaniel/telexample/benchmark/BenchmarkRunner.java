package cl.baezdaniel.telexample.benchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Benchmark Runner Utility
 * 
 * This utility can be used to run benchmarks programmatically or as a standalone application.
 * 
 * Usage:
 * - Run via Gradle: `./gradlew benchmark`
 * - Run specific test: `./gradlew benchmark --tests "*LowLoad*"`
 * - Run with system properties: `./gradlew benchmark -Dtelemetry.queue.enabled=false`
 */
@SpringBootApplication
@ComponentScan(basePackages = "cl.baezdaniel.telexample")
public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("🚀 TELEMETRY PROCESSING BENCHMARK RUNNER");
        System.out.println("═".repeat(50));
        System.out.println("This utility benchmarks queue-based vs synchronous processing");
        System.out.println("Run tests via: ./gradlew benchmark");
        System.out.println("═".repeat(50));
        
        // Print current configuration
        String queueEnabled = System.getProperty("telemetry.queue.enabled", "not set");
        System.out.println("Queue processing: " + queueEnabled);
        
        // You could extend this to run benchmarks programmatically
        // For now, it's just a utility for configuration
        System.out.println("Use Gradle tasks to run actual benchmarks.");
    }
} 