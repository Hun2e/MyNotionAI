package com.mynotionai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderService reminderService;

    @Scheduled(cron = "0 * * * * *")
    public void runReminderJob() {
        try {
            reminderService.sendOneHourReminders();
        } catch (Exception e) {
            log.error("Reminder job failed", e);
        }
    }
}
