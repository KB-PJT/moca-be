package com.moca.mocabe.domain.notification.service;

import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.moca.mocabe.domain.notification.type.TimeSlot;
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

        scheduler.performanceDeadlineNotification();
        scheduler.lunchBenefitNotification();
        scheduler.dinnerBenefitNotification();

        verify(service).sendPerformanceDeadlineNotifications();
        verify(service).sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);
        verify(service).sendTimeBasedBenefitNotifications(TimeSlot.DINNER);
    }

    @Test
    @DisplayName("월말·점심·저녁 Scheduler는 Asia/Seoul 정책 시각을 사용한다")
    void usesConfiguredSeoulSchedules() throws Exception {
        assertSchedule("performanceDeadlineNotification", "0 0 20 * * *");
        assertSchedule("lunchBenefitNotification", "0 30 11 * * *");
        assertSchedule("dinnerBenefitNotification", "0 0 18 * * *");
    }

    private void assertSchedule(String methodName, String expectedCron) throws Exception {
        Scheduled scheduled = NotificationScheduler.class.getMethod(methodName).getAnnotation(Scheduled.class);
        assertEquals(expectedCron, scheduled.cron());
        assertEquals("Asia/Seoul", scheduled.zone());
    }
}
