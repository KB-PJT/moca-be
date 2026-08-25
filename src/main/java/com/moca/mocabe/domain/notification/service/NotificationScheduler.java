package com.moca.mocabe.domain.notification.service;

import com.moca.mocabe.domain.notification.type.TimeSlot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;

public class NotificationScheduler {
    private final NotificationService service;
    private final Clock clock;

    public NotificationScheduler(NotificationService service) {
        this(service, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    NotificationScheduler(NotificationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void morningBenefitNotification() {
        service.sendTimeBasedBenefitNotifications(TimeSlot.MORNING);
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

    @Scheduled(cron = "0 0,10,20,30,40,50 16 * * *", zone = "Asia/Seoul")
    public void afternoonBenefitNotificationFirstMessage() {
        sendAfternoonPreviewIfToday(true);
    }

    @Scheduled(cron = "0 5,15,25,35,45,55 16 * * *", zone = "Asia/Seoul")
    public void afternoonBenefitNotificationSecondMessage() {
        sendAfternoonPreviewIfToday(false);
    }

    @Scheduled(cron = "0 0,10,20,30,40,50 17 * * *", zone = "Asia/Seoul")
    public void afternoonBenefitNotificationFirstMessageAtFive() {
        sendAfternoonPreviewIfToday(true);
    }

    @Scheduled(cron = "0 5,15,25,35,45,55 17 * * *", zone = "Asia/Seoul")
    public void afternoonBenefitNotificationSecondMessageAtFive() {
        sendAfternoonPreviewIfToday(false);
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Seoul")
    public void afternoonBenefitNotificationAtSix() {
        sendAfternoonPreviewIfToday(true);
    }

    private void sendAfternoonPreviewIfToday(boolean firstMessage) {
        if (LocalDate.now(clock).equals(LocalDate.of(2026, 8, 25))) {
            service.sendAfternoonPreviewNotifications(firstMessage);
        }
    }
}
