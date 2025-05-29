You are an expert Java developer using Spring Boot and JUnit 5.
Generate a test suite for the Telexample service with these specs:

1. Test class: TelemetryControllerTest in package cl.danielbaez.telexample.
2. Use @SpringBootTest and @AutoConfigureMockMvc for integration tests.
3. Scenarios:
   a) POST /telemetry with missing deviceId → 400 Bad Request.
   b) POST /telemetry with valid JSON (deviceId, latitude, longitude, timestamp) → 201 Created, response body contains numeric "id".
   c) GET /devices/{deviceId}/telemetry/latest when no data → 404 Not Found.
   d) GET /devices/{deviceId}/telemetry/latest after two posts (timestamps T1, T2) → 200 OK, returns record with T2.
4. Include setup to initialize an embedded SQLite DataSource and load schema.sql.
5. Use @BeforeEach to reset DB state between tests.
6. Use jsonPath assertions for response validation.

Provide all necessary imports, annotations, and helper methods so the tests compile and run out of the box.
