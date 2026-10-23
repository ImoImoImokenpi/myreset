package com.olafreset.config;

import com.olafreset.service.ReminderService;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 毎分、リマインダーの時刻になったユーザーに通知する。テストでは無効にする。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {

    private final ReminderService reminders;

    SchedulingConfig(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    void sendReminders() {
        reminders.sendDue(LocalDateTime.now());
    }
}
