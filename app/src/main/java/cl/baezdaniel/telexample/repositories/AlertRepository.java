package cl.baezdaniel.telexample.repositories;

import cl.baezdaniel.telexample.entities.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    // Basic queries
    List<Alert> findByDeviceId(String deviceId);
    List<Alert> findByDeviceIdOrderByCreatedAtDesc(String deviceId);
    
    // Pagination queries
    Page<Alert> findByDeviceId(String deviceId, Pageable pageable);
    Page<Alert> findByDeviceIdAndAlertType(String deviceId, String alertType, Pageable pageable);
    Page<Alert> findBySeverity(String severity, Pageable pageable);
    Page<Alert> findByAlertType(String alertType, Pageable pageable);
    
    // Deduplication
    Optional<Alert> findByFingerprint(String fingerprint);
    boolean existsByFingerprint(String fingerprint);
    
    // Retention management
    @Query("DELETE FROM Alert a WHERE a.createdAt < :cutoffDate")
    @Modifying
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Count older alerts for reporting
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.createdAt < :cutoffDate")
    long countOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Advanced queries for API
    @Query("SELECT a FROM Alert a WHERE a.deviceId = :deviceId AND a.createdAt BETWEEN :start AND :end")
    Page<Alert> findByDeviceIdAndDateRange(
        @Param("deviceId") String deviceId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        Pageable pageable
    );
    
    // Multi-filter query for advanced API
    @Query("SELECT a FROM Alert a WHERE " +
           "(:deviceId IS NULL OR a.deviceId = :deviceId) AND " +
           "(:alertType IS NULL OR a.alertType = :alertType) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate)")
    Page<Alert> findWithFilters(
        @Param("deviceId") String deviceId,
        @Param("alertType") String alertType,
        @Param("severity") String severity,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    // Statistics queries
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.deviceId = :deviceId")
    long countByDeviceId(@Param("deviceId") String deviceId);
    
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.severity = :severity")
    long countBySeverity(@Param("severity") String severity);
    
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.alertType = :alertType")
    long countByAlertType(@Param("alertType") String alertType);
    
    // Recent alerts (last 24 hours)
    @Query("SELECT a FROM Alert a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<Alert> findRecentAlerts(@Param("since") LocalDateTime since);
    
    @Query("SELECT a FROM Alert a WHERE a.deviceId = :deviceId AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<Alert> findRecentAlertsForDevice(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    // Additional methods needed by tests
    Page<Alert> findByDeviceIdAndAlertTypeAndCreatedAtBetween(
        String deviceId, 
        String alertType, 
        LocalDateTime startDate, 
        LocalDateTime endDate, 
        Pageable pageable
    );

    Page<Alert> findByCreatedAtBetween(
        LocalDateTime startDate, 
        LocalDateTime endDate, 
        Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM Alert a WHERE a.createdAt < :cutoffDate")
    void deleteAlertsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    Page<Alert> findByDeviceIdAndCreatedAtBetween(
        String deviceId, 
        LocalDateTime startDate, 
        LocalDateTime endDate, 
        Pageable pageable
    );
} 