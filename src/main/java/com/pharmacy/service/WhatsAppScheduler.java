package com.pharmacy.service;

import com.pharmacy.model.StoreConfig;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class WhatsAppScheduler {
    private final PharmacyService pharmacyService;
    private LocalDate lastRunDate;

    public WhatsAppScheduler(PharmacyService pharmacyService) {
        this.pharmacyService = pharmacyService;
    }

    // Runs every minute to check if the current time matches the user's scheduled daily reminder time
    @Scheduled(cron = "0 * * * * ?")
    public void checkAndSendDailyReminders() {
        try {
            StoreConfig config = pharmacyService.getStoreConfigEntity();
            if (!config.enableAutoReminders) {
                return;
            }

            LocalDate today = LocalDate.now();
            if (today.equals(lastRunDate)) {
                return; // Already executed today
            }

            String timeStr = config.dailyReminderTime != null ? config.dailyReminderTime : "09:00";
            LocalTime reminderTime = LocalTime.parse(timeStr);
            LocalTime now = LocalTime.now();

            // Run if it's equal to or after the scheduled time
            if (now.isAfter(reminderTime) || now.equals(reminderTime)) {
                System.out.println("[Scheduler] Scheduled time (" + timeStr + ") reached. Triggering automated daily reminders...");
                pharmacyService.triggerDailyReminders();
                lastRunDate = today;
                System.out.println("[Scheduler] Automated daily reminders execution completed.");
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Error checking/executing scheduled daily reminders: " + e.getMessage());
        }
    }
}
