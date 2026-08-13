package com.moca.mocabe.domain.notification.service;

import com.moca.mocabe.domain.notification.type.TimeSlot;
import org.springframework.scheduling.annotation.Scheduled;

public class NotificationScheduler {
    private final NotificationService service;

    public NotificationScheduler(NotificationService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void performanceDeadlineNotification() {
        service.sendPerformanceDeadlineNotifications();
    }

    @Scheduled(cron = "0 30 11 * * *", zone = "Asia/Seoul")
    public void lunchBenefitNotification() {
        service.sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Seoul")
    public void dinnerBenefitNotification() {
        service.sendTimeBasedBenefitNotifications(TimeSlot.DINNER);
    }
}
