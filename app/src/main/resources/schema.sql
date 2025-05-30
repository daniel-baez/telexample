-- H2 Database Schema
CREATE TABLE IF NOT EXISTS telemetry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    latitude DOUBLE,
    longitude DOUBLE,
    processor_name VARCHAR(100),
    fingerprint VARCHAR(255),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
); 