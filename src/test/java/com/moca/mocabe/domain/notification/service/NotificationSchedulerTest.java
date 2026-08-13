package com.moca.mocabe.domain.notification.service;

import static org.mockito.Mockito.verify;

import com.moca.mocabe.domain.notification.type.TimeSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
