package com.moca.mocabe.domain.notification.service;

import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.notification.type.TimeSlot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

@DisplayName("알림 Scheduler")
class NotificationSchedulerTest {
    @Test
    @DisplayName("각 Scheduler 메서드는 대응하는 알림 서비스를 호출한다")
    void delegatesToNotificationService() {
        NotificationService service = org.mockito.Mockito.mock(NotificationService.class);
        NotificationScheduler scheduler = new NotificationScheduler(service);

        scheduler.morningBenefitNotification();
        scheduler.performanceDeadlineNotification();
        scheduler.lunchBenefitNotification();
        scheduler.dinnerBenefitNotification();

        verify(service).sendPerformanceDeadlineNotifications();
        verify(service).sendTimeBasedBenefitNotifications(TimeSlot.MORNING);
        verify(service).sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);
        verify(service).sendTimeBasedBenefitNotifications(TimeSlot.DINNER);
    }

    @Test
    @DisplayName("월말·점심·저녁 Scheduler는 Asia/Seoul 정책 시각을 사용한다")
    void usesConfiguredSeoulSchedules() throws Exception {
        assertSchedule("morningBenefitNotification", "0 0 8 * * *");
        assertSchedule("performanceDeadlineNotification", "0 0 20 * * *");
        assertSchedule("lunchBenefitNotification", "0 30 11 * * *");
        assertSchedule("dinnerBenefitNotification", "0 0 18 * * *");
    }

    private void assertSchedule(String methodName, String expectedCron) throws Exception {
        Scheduled scheduled = NotificationScheduler.class.getMethod(methodName).getAnnotation(Scheduled.class);
        assertEquals(expectedCron, scheduled.cron());
        assertEquals("Asia/Seoul", scheduled.zone());
    }

    @Test
    @DisplayName("오후 알림은 두 멘트를 번갈아 호출한다")
    void runsAlternatingAfternoonPreviews() {
        NotificationService service = org.mockito.Mockito.mock(NotificationService.class);
        NotificationScheduler scheduler = new NotificationScheduler(service,
                Clock.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneId.of("Asia/Seoul")));

        scheduler.afternoonBenefitNotificationFirstMessage();
        scheduler.afternoonBenefitNotificationSecondMessage();
        scheduler.afternoonBenefitNotificationFirstMessageAtFive();
        scheduler.afternoonBenefitNotificationSecondMessageAtFive();
        scheduler.afternoonBenefitNotificationAtSix();

        verify(service, org.mockito.Mockito.times(3)).sendAfternoonPreviewNotifications(true);
        verify(service, org.mockito.Mockito.times(2)).sendAfternoonPreviewNotifications(false);
    }

    @Test
    @DisplayName("오늘이 아니면 오후 미리보기를 보내지 않는다")
    void skipsAfternoonPreviewsOnOtherDates() {
        NotificationService service = org.mockito.Mockito.mock(NotificationService.class);
        NotificationScheduler scheduler = new NotificationScheduler(service,
                Clock.fixed(Instant.parse("2026-08-26T08:00:00Z"), ZoneId.of("Asia/Seoul")));

        scheduler.afternoonBenefitNotificationFirstMessage();

        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
