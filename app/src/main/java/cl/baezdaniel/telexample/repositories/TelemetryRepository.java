package cl.baezdaniel.telexample.repositories;

import cl.baezdaniel.telexample.entities.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    
    @Query("SELECT t FROM Telemetry t WHERE t.deviceId = :deviceId ORDER BY t.timestamp DESC LIMIT 1")
    Optional<Telemetry> findLatestByDeviceId(@Param("deviceId") String deviceId);
} 