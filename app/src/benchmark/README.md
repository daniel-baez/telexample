# Telemetry Processing Benchmark

This benchmark suite compares the performance of **queue-based** vs **synchronous** telemetry processing across different load scenarios.

## 🎯 Purpose

The benchmark answers the key question: **"Does adding the queue actually make the system faster or slower?"**

## 🚀 Quick Start

### Run All Benchmarks
```bash
./gradlew benchmark
```

### Run Specific Benchmark Categories
```bash
# Low load comparison (100 events)
./gradlew benchmark --tests "*LowLoad*"

# Medium load comparison (1,000 events)  
./gradlew benchmark --tests "*MediumLoad*"

# High load comparison (10,000 events)
./gradlew benchmark --tests "*HighLoad*" 

# Concurrent users comparison
./gradlew benchmark --tests "*Concurrent*"
```

### Run Only Queue-Based Tests
```bash
./gradlew benchmark --tests "*Queue*"
```

### Run Only Synchronous Tests
```bash
./gradlew benchmark --tests "*Sync*"
```

## 📊 Benchmark Scenarios

| Scenario | Events | Threads | Purpose |
|----------|---------|---------|---------|
| **Low Load** | 100 | 1 | Baseline comparison for light usage |
| **Medium Load** | 1,000 | 1 | Typical production workload |
| **High Load** | 10,000 | 1 | Stress testing, throughput limits |
| **Concurrent** | 1,000 | 8 | Multi-user simulation |

## 📈 Metrics Measured

- **Response Time**: Mean, P50, P95, P99 percentiles
- **Throughput**: Events processed per second
- **Success Rate**: Percentage of successful requests
- **Total Time**: End-to-end benchmark duration

## 🧪 Expected Results

### Queue-Based Processing Advantages:
- ✅ **Higher throughput** under load (especially >1000 events)
- ✅ **Better concurrent performance** with multiple threads
- ✅ **Faster response times** (returns immediately with 202 Accepted)
- ✅ **More stable** under high load

### Synchronous Processing Advantages:
- ✅ **Lower latency** for individual requests (end-to-end processing)
- ✅ **Simpler architecture** and debugging
- ✅ **Better for low-load scenarios** (< 100 events)
- ✅ **Immediate consistency** (data available instantly)

## 🔧 Configuration

The benchmark uses `application-benchmark.properties` with optimized settings:

```properties
# Database: In-memory H2 for speed
spring.datasource.url=jdbc:h2:mem:benchmarkdb

# Queue Configuration (toggleable)
telemetry.queue.enabled=true
telemetry.queue.capacity=10000
telemetry.queue.workers=8

# Optimized thread pools
telemetry.processing.core-pool-size=32
telemetry.processing.max-pool-size=64
```

## 📋 Sample Output

```
🏆 BENCHMARK SUMMARY
═══════════════════════════════════════════════════════════════════════════════════
Test                 Mean (ms)   P95 (ms)   P99 (ms)  Throughput    Total Time
────────────────────────────────────────────────────────────────────────────────────
Queue-LowLoad             2.45          8         12       127.34          786
Sync-LowLoad              5.12         15         23        89.67         1115
Queue-MediumLoad          1.89          6         10       543.21         1841
Sync-MediumLoad           4.67         18         25       198.45         5040
Queue-HighLoad            1.23          4          8      2847.39         3513
Sync-HighLoad             3.89         22         35       487.23        20523
────────────────────────────────────────────────────────────────────────────────────

📊 PERFORMANCE ANALYSIS
══════════════════════════════════════════════════════

Low Load (100 events):
  Throughput: Queue 42.01% better than Sync
  Response Time: Queue 52.15% better than Sync
  🚀 Queue processing shows significant throughput advantage

Medium Load (1,000 events):
  Throughput: Queue 173.67% better than Sync  
  Response Time: Queue 59.53% better than Sync
  🚀 Queue processing shows significant throughput advantage

High Load (10,000 events):
  Throughput: Queue 484.32% better than Sync
  Response Time: Queue 68.38% better than Sync
  🚀 Queue processing shows significant throughput advantage
```

## 🛠️ Advanced Usage

### Custom JVM Settings
```bash
./gradlew benchmark -Dorg.gradle.jvmargs="-Xmx8g -XX:+UseG1GC"
```

### Debug Mode
```bash
./gradlew benchmark --debug-jvm
```

### Continuous Benchmarking
```bash
# Run benchmarks 5 times for statistical significance
for i in {1..5}; do
  echo "Benchmark run $i"
  ./gradlew clean benchmark
done
```

## 📝 Interpretation Guide

### When Queue Processing Wins:
- **High throughput scenarios** (>500 events/second)
- **Concurrent users** (multiple simultaneous requests)  
- **Batch processing** workloads
- **Response time SLA** requirements (need <10ms responses)

### When Synchronous Processing Wins:
- **Low-latency** requirements (need immediate data availability)
- **Simple debugging** needs
- **Small workloads** (<100 events)
- **Strong consistency** requirements

### Trade-off Analysis:
Queue processing trades **end-to-end latency** for **throughput and responsiveness**.
Choose based on your specific use case and requirements.

## 🔍 Troubleshooting

### Common Issues:

1. **OutOfMemoryError**: Increase JVM heap size
2. **Test timeouts**: Increase `junit.jupiter.execution.timeout.default`
3. **Database locks**: Check H2 configuration
4. **Port conflicts**: Ensure no other Spring Boot apps running

### Debug Commands:
```bash
# Check current configuration
./gradlew benchmark --info

# Run with detailed logging
./gradlew benchmark -Dlogging.level.cl.baezdaniel.telexample=DEBUG
``` 