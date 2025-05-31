# Telemetry Service

A Spring Boot REST API for managing device telemetry data with SQLite database.

## Endpoints

### POST /telemetry
Submit telemetry data from a device.

**Request Body:**
```json
{
  "deviceId": "string (required)",
  "latitude": "number (required)",
  "longitude": "number (required)", 
  "timestamp": "ISO datetime string (required)"
}
```

**Responses:**
- `202 Accepted` - Returns `{}` for successful creation
- `400 Bad Request` - When required fields are missing or invalid

**Example:**
```bash
curl -X POST http://localhost:8080/telemetry \
  -H "Content-Type: application/json" \
  -d '{"deviceId": "device123", "latitude": 40.7128, "longitude": -74.0060, "timestamp": "2023-12-01T10:00:00"}'
```

### GET /devices/{deviceId}/telemetry/latest
Retrieve the most recent telemetry data for a specific device.

**Path Parameters:**
- `deviceId` - The device identifier

**Responses:**
- `200 OK` - Returns the latest telemetry record
- `404 Not Found` - When no telemetry data exists for the device

**Example:**
```bash
curl http://localhost:8080/devices/device123/telemetry/latest
```

**Response:**
```json
{
  "id": 1,
  "deviceId": "device123",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "timestamp": "2023-12-01T10:00:00"
}
```

## Running the Application

### Start the server:
```bash
./gradlew bootRun
```

### Run tests:
```bash
./gradlew test
```

### Build the project:
```bash
./gradlew build
```

The application will start on `http://localhost:8080` and use an SQLite database file `telemetry.db` for data persistence.

# configuration options

The system is highly configurable featuring the following properties for customization

- ``