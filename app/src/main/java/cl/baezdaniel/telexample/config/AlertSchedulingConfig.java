package cl.baezdaniel.telexample.config;

import cl.baezdaniel.telexample.services.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for scheduled alert management tasks.
 * Handles automated cleanup of old alerts according to retention policies.
 */
@Configuration
@EnableScheduling
public class AlertSchedulingConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertSchedulingConfig.class);
    
    @Autowired
    private AlertService alertService;
    
    /**
     * Scheduled task to clean up old alerts.
     * Runs daily at 2 AM to remove alerts older than 3 months.
     * 
     * Cron expression: "0 0 2 * * ?" = Second Minute Hour Day Month DayOfWeek
     * - 0 seconds
     * - 0 minutes  
     * - 2 hours (2 AM)
     * - * any day of month
     * - * any month
     * - ? any day of week
     */
    @Scheduled(cron = "${alert.cleanup.schedule:0 0 2 * * ?}")
    public void cleanupOldAlerts() {
        try {
            logger.info("🧹 Starting scheduled cleanup of old alerts...");
            
            long startTime = System.currentTimeMillis();
            int deletedCount = alertService.cleanupOldAlerts();
            long duration = System.currentTimeMillis() - startTime;
            
            if (deletedCount > 0) {
                logger.info("🗑️ Alert cleanup completed: {} alerts deleted in {}ms", deletedCount, duration);
            } else {
                logger.info("✅ Alert cleanup completed: No old alerts found to delete");
            }
            
        } catch (Exception e) {
            logger.error("❌ Error during scheduled alert cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Optional: Health check task to report alert statistics.
     * Runs every hour to log current alert counts for monitoring.
     */
    @Scheduled(cron = "${alert.health.schedule:0 0 * * * ?}")
    public void logAlertStatistics() {
        try {
            // This could be expanded to include more detailed statistics
            logger.debug("📊 Alert system health check - scheduled task running normally");
            
            // Future enhancement: Add alert count monitoring, rate limiting checks, etc.
            // long totalAlerts = alertService.getTotalAlertCount();
            // logger.info("📈 Alert Statistics: Total alerts in system: {}", totalAlerts);
            
        } catch (Exception e) {
            logger.warn("⚠️ Error during alert statistics logging: {}", e.getMessage());
        }
    }
} 