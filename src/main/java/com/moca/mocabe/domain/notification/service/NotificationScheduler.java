package com.moca.mocabe.domain.notification.service;

import com.moca.mocabe.domain.notification.type.TimeSlot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

public class NotificationScheduler {
    private final NotificationService service;
    private final Clock clock;
    private final String morningPreviewDate;

    public NotificationScheduler(NotificationService service) {
        this(service, Clock.system(ZoneId.of("Asia/Seoul")), "");
    }

    public NotificationScheduler(NotificationService service, Clock clock,
                                  @Value("${moca.notification.morning-preview-date:}") String morningPreviewDate) {
        this.service = service;
        this.clock = clock;
        this.morningPreviewDate = morningPreviewDate;
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

    @Scheduled(cron = "0 50,55 17 * * *", zone = "Asia/Seoul")
    public void morningBenefitPreviewBeforeSix() {
        sendMorningPreviewIfEnabledToday();
    }

    @Scheduled(cron = "0 0,5,10,15,20,25,30 18 * * *", zone = "Asia/Seoul")
    public void morningBenefitPreviewAfterSix() {
        sendMorningPreviewIfEnabledToday();
    }

    private void sendMorningPreviewIfEnabledToday() {
        if (!morningPreviewDate.isBlank() && LocalDate.now(clock).toString().equals(morningPreviewDate)) {
            service.sendMorningPreviewNotifications();
        }
    }
}
