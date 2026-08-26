package com.moca.mocabe.domain.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.dto.MerchantCardRecommendationBatchResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantCardRecommendationResponse;
import com.moca.mocabe.domain.merchant.dto.NearbyMerchantResponse;
import com.moca.mocabe.domain.merchant.dto.RankedCardBenefitResponse;
import com.moca.mocabe.domain.merchant.model.MerchantCategoryRow;
import com.moca.mocabe.domain.merchant.service.MerchantCardRecommendationService;
import com.moca.mocabe.domain.merchant.service.MerchantNearbyQueryService;
import com.moca.mocabe.domain.notification.mapper.DeviceMapper;
import com.moca.mocabe.domain.notification.mapper.NotificationMapper;
import com.moca.mocabe.domain.notification.model.PerformanceDeadlineCandidate;
import com.moca.mocabe.domain.notification.model.UserDevice;
import com.moca.mocabe.domain.notification.type.TimeSlot;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("알림 발송 서비스")
class NotificationServiceTest {
    @Test
    @DisplayName("월말 D-3 실적 미달 카드의 활성 기기에 알림을 발송한다")
    void sendsPerformanceDeadline() throws Exception {
        NotificationMapper history = org.mockito.Mockito.mock(NotificationMapper.class);
        DeviceMapper devices = org.mockito.Mockito.mock(DeviceMapper.class);
        FcmService fcm = org.mockito.Mockito.mock(FcmService.class);
        when(history.findPerformanceDeadlineCandidates("2026-08")).thenReturn(List.of(
                new PerformanceDeadlineCandidate("user", "card", "카드", new BigDecimal("120000"),
                        new BigDecimal("150000"))));
        when(devices.findActiveByUserId("user")).thenReturn(List.of(new UserDevice("device", "user", "token", "WEB")));
        when(history.existsSent("user", "device", "PERFORMANCE_DEADLINE", "card", "2026-08-28", null))
                .thenReturn(false);
        when(history.claimPending(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(fcm.send(eq("token"), any(), any(), any())).thenReturn("message");
        service(history, devices, fcm, Instant.parse("2026-08-28T11:00:00Z")).sendPerformanceDeadlineNotifications();
        verify(fcm).send(eq("token"), any(), any(), any());
        verify(history).updateHistory(any(), eq("SENT"), eq("message"), eq(null));
    }

    @Test
    @DisplayName("D-3이 아니면 실적 알림 대상을 조회하지 않는다")
    void skipsOutsideDeadlineDay() {
        NotificationMapper history = org.mockito.Mockito.mock(NotificationMapper.class);
        service(history, org.mockito.Mockito.mock(DeviceMapper.class), org.mockito.Mockito.mock(FcmService.class),
                Instant.parse("2026-08-29T11:00:00Z")).sendPerformanceDeadlineNotifications();
        verify(history, never()).findPerformanceDeadlineCandidates(any());
    }

    @Test
    @DisplayName("DB 선점에 실패하면 FCM을 호출하지 않는다")
    void skipsWhenClaimFails() throws Exception {
        Fixture fixture = fixture();
        fixture.performanceCandidate();
        when(fixture.history.claimPending(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        fixture.service.sendPerformanceDeadlineNotifications();
        verify(fixture.fcm, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("만료 FCM 토큰은 비활성화하고 실패 이력을 남긴다")
    void deactivatesInvalidToken() throws Exception {
        Fixture fixture = fixture();
        fixture.performanceCandidate();
        fixture.claims();
        FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(fixture.fcm.send(any(), any(), any(), any())).thenThrow(exception);
        when(fixture.fcm.isInvalidToken(exception)).thenReturn(true);
        fixture.service.sendPerformanceDeadlineNotifications();
        verify(fixture.devices).deactivate("device", "user");
        verify(fixture.history).updateHistory(any(), eq("FAILED"), eq(null), eq("UNREGISTERED"));
    }

    @Test
    @DisplayName("일반 FCM 오류도 실패 이력을 남기고 다음 실행을 유지한다")
    void recordsGeneralFailure() throws Exception {
        Fixture fixture = fixture();
        fixture.performanceCandidate();
        fixture.claims();
        when(fixture.fcm.send(any(), any(), any(), any())).thenThrow(new IllegalStateException("failure"));
        fixture.service.sendPerformanceDeadlineNotifications();
        verify(fixture.history).updateHistory(any(), eq("FAILED"), eq(null), eq("IllegalStateException"));
    }

    @Test
    @DisplayName("FCM 오류 코드가 없으면 예외 유형으로 실패 이력을 남긴다")
    void recordsFirebaseFailureWithoutErrorCode() throws Exception {
        Fixture fixture = fixture();
        fixture.performanceCandidate();
        fixture.claims();
        FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(fixture.fcm.send(any(), any(), any(), any())).thenThrow(exception);
        fixture.service.sendPerformanceDeadlineNotifications();
        verify(fixture.history).updateHistory(any(), eq("FAILED"), eq(null), eq("FirebaseMessagingException"));
    }

    @Test
    @DisplayName("첫 기기 발송 실패가 같은 사용자의 다음 기기 발송을 막지 않는다")
    void isolatesDeviceFailure() throws Exception {
        Fixture fixture = fixture();
        fixture.performanceCandidate();
        when(fixture.devices.findActiveByUserId("user")).thenReturn(List.of(
                new UserDevice("first", "user", "first-token", "WEB"),
                new UserDevice("second", "user", "second-token", "ANDROID")));
        fixture.claims();
        when(fixture.fcm.send(eq("first-token"), any(), any(), any()))
                .thenThrow(new IllegalStateException("failure"));
        when(fixture.fcm.send(eq("second-token"), any(), any(), any())).thenReturn("second-message");

        fixture.service.sendPerformanceDeadlineNotifications();

        verify(fixture.fcm).send(eq("first-token"), any(), any(), any());
        verify(fixture.fcm).send(eq("second-token"), any(), any(), any());
        ArgumentCaptor<String> deliveryKeys = ArgumentCaptor.forClass(String.class);
        verify(fixture.history, times(2)).claimPending(any(), deliveryKeys.capture(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        assertNotEquals(deliveryKeys.getAllValues().get(0), deliveryKeys.getAllValues().get(1));
    }

    @Test
    @DisplayName("활성 FCM 기기가 없는 사용자는 실적 알림을 발송하지 않는다")
    void skipsPerformanceCandidateWithoutActiveDevice() throws Exception {
        Fixture fixture = fixture();
        when(fixture.history.findPerformanceDeadlineCandidates("2026-08")).thenReturn(List.of(
                new PerformanceDeadlineCandidate("user", "card", "카드", new BigDecimal("120000"),
                        new BigDecimal("150000"))));
        when(fixture.devices.findActiveByUserId("user")).thenReturn(List.of());

        fixture.service.sendPerformanceDeadlineNotifications();

        verify(fixture.fcm, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("최근 위치 주변에 보유 카드 혜택이 있으면 시간대 알림을 발송한다")
    void sendsTimeBasedBenefit() throws Exception {
        Fixture fixture = fixture();
        fixture.nearbyDevice();
        when(fixture.locations.find("user")).thenReturn(Optional.of(new UserLocationService.Location(35.1, 129.1)));
        when(fixture.categories.findAllOrderedByDisplayOrder()).thenReturn(List.of(
                new MerchantCategoryRow("failed", "FAIL", "실패", 1),
                new MerchantCategoryRow("category", "CAFE", "카페", 2)));
        when(fixture.nearby.getNearbyMerchants(eq("failed"), any(), any(), eq(500), eq(null)))
                .thenThrow(new IllegalStateException("failure"));
        when(fixture.nearby.getNearbyMerchants(eq("category"), any(), any(), eq(500), eq(null)))
                .thenReturn(List.of(new NearbyMerchantResponse("merchant", "가맹점", 35.1, 129.1, 10, "주소")));
        MerchantCardRecommendationResponse recommendation = org.mockito.Mockito.mock(
                MerchantCardRecommendationResponse.class);
        when(recommendation.recommendedCard()).thenReturn(org.mockito.Mockito.mock(RankedCardBenefitResponse.class));
        when(fixture.recommendations.recommendBatch(eq("user"), any(), eq(null))).thenReturn(
                new MerchantCardRecommendationBatchResponse(List.of(recommendation)));
        fixture.claims();
        when(fixture.fcm.send(any(), any(), any(), any())).thenReturn("message");
        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);
        verify(fixture.fcm).send(eq("token"), any(), any(), any());
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(fixture.fcm).send(eq("token"), any(), any(), payload.capture());
        assertEquals("TIME_BASED_BENEFIT", payload.getValue().get("type"));
        assertEquals("MAP", payload.getValue().get("target"));
    }

    @Test
    @DisplayName("시간대별 알림은 지정된 제목을 사용한다")
    void usesTimeSlotTitles() throws Exception {
        Fixture fixture = fixture();
        fixture.eligibleNearbyBenefit();
        fixture.claims();
        when(fixture.fcm.send(any(), any(), any(), any())).thenReturn("message");

        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.MORNING);
        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);
        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.DINNER);

        ArgumentCaptor<String> titles = ArgumentCaptor.forClass(String.class);
        verify(fixture.fcm, times(3)).send(eq("token"), titles.capture(), any(), any());
        assertEquals(List.of("출근길, 커피 혜택을 확인해보세요", "점심시간, 놓치고 있는 혜택을 확인해보세요",
                "오늘 마지막 결제도 혜택 놓치지 마세요"), titles.getAllValues());
    }

    @Test
    @DisplayName("오후 미리보기는 실행 시각을 delivery 식별자로 사용한다")
    void sendsAfternoonPreviewWithRunReference() throws Exception {
        Fixture fixture = fixture();
        fixture.eligibleNearbyBenefit();
        fixture.claims();
        when(fixture.fcm.send(any(), any(), any(), any())).thenReturn("message");

        fixture.service.sendAfternoonPreviewNotifications(true);

        verify(fixture.history).existsSent("user", "device", "TIME_BASED_BENEFIT", "afternoon-20:00",
                "2026-08-28", "MORNING");
        verify(fixture.fcm).send(eq("token"), eq("출근길, 커피 혜택을 확인해보세요"), any(), any());
    }

    @Test
    @DisplayName("주변 가맹점에 추천 가능한 보유 카드가 없으면 발송하지 않는다")
    void skipsNearbyMerchantWithoutEligibleCard() throws Exception {
        Fixture fixture = fixture();
        fixture.nearbyDevice();
        when(fixture.locations.find("user")).thenReturn(Optional.of(new UserLocationService.Location(35.1, 129.1)));
        when(fixture.categories.findAllOrderedByDisplayOrder()).thenReturn(List.of(
                new MerchantCategoryRow("category", "CAFE", "카페", 1)));
        when(fixture.nearby.getNearbyMerchants(eq("category"), any(), any(), eq(500), eq(null)))
                .thenReturn(List.of(new NearbyMerchantResponse("merchant", "가맹점", 35.1, 129.1, 10, "주소")));
        MerchantCardRecommendationResponse recommendation = org.mockito.Mockito.mock(
                MerchantCardRecommendationResponse.class);
        when(fixture.recommendations.recommendBatch(eq("user"), any(), eq(null))).thenReturn(
                new MerchantCardRecommendationBatchResponse(List.of(recommendation)));
        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);
        verify(fixture.fcm, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("500m 내 가맹점이 없으면 추천 조회와 시간대 알림을 건너뛴다")
    void skipsWhenNoNearbyMerchantExists() throws Exception {
        Fixture fixture = fixture();
        fixture.nearbyDevice();
        when(fixture.locations.find("user")).thenReturn(Optional.of(new UserLocationService.Location(35.1, 129.1)));
        when(fixture.categories.findAllOrderedByDisplayOrder()).thenReturn(List.of(
                new MerchantCategoryRow("category", "CAFE", "카페", 1)));
        when(fixture.nearby.getNearbyMerchants(eq("category"), any(), any(), eq(500), eq(null)))
                .thenReturn(List.of());

        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);

        verify(fixture.recommendations, never()).recommendBatch(any(), any(), any());
        verify(fixture.fcm, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 날 점심 알림 이후 저녁 알림을 별도로 발송한다")
    void sendsLunchAndDinnerSeparately() throws Exception {
        Fixture fixture = fixture();
        fixture.eligibleNearbyBenefit();
        fixture.claims();
        when(fixture.fcm.send(any(), any(), any(), any())).thenReturn("message");

        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.LUNCH);
        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.DINNER);

        verify(fixture.fcm, times(2)).send(eq("token"), any(), any(), any());
        verify(fixture.history).claimPending(any(), any(), any(), any(), any(), any(), eq("LUNCH"),
                any(), any(), any(), any());
        verify(fixture.history).claimPending(any(), any(), any(), any(), any(), any(), eq("DINNER"),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("첫 사용자 FCM 실패 후에도 다음 사용자의 시간대 알림을 발송한다")
    void continuesWithNextUserAfterFcmFailure() throws Exception {
        Fixture fixture = fixture();
        UserDevice first = new UserDevice("first", "first-user", "first-token", "WEB");
        UserDevice second = new UserDevice("second", "second-user", "second-token", "WEB");
        when(fixture.devices.findActiveNearbyBenefitDevices()).thenReturn(List.of(first, second));
        when(fixture.locations.find(any())).thenReturn(Optional.of(new UserLocationService.Location(35.1, 129.1)));
        when(fixture.categories.findAllOrderedByDisplayOrder()).thenReturn(List.of(
                new MerchantCategoryRow("category", "CAFE", "카페", 1)));
        when(fixture.nearby.getNearbyMerchants(eq("category"), any(), any(), eq(500), eq(null)))
                .thenReturn(List.of(new NearbyMerchantResponse("merchant", "가맹점", 35.1, 129.1, 10, "주소")));
        MerchantCardRecommendationResponse recommendation = org.mockito.Mockito.mock(
                MerchantCardRecommendationResponse.class);
        when(recommendation.recommendedCard()).thenReturn(org.mockito.Mockito.mock(RankedCardBenefitResponse.class));
        when(fixture.recommendations.recommendBatch(any(), any(), eq(null))).thenReturn(
                new MerchantCardRecommendationBatchResponse(List.of(recommendation)));
        fixture.claims();
        when(fixture.fcm.send(eq("first-token"), any(), any(), any()))
                .thenThrow(new IllegalStateException("failure"));
        when(fixture.fcm.send(eq("second-token"), any(), any(), any())).thenReturn("message");

        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.DINNER);

        verify(fixture.fcm).send(eq("first-token"), any(), any(), any());
        verify(fixture.fcm).send(eq("second-token"), any(), any(), any());
    }

    @Test
    @DisplayName("위치가 없거나 이미 발송됐거나 사용자 조회가 실패하면 시간대 알림을 건너뛴다")
    void skipsIneligibleTimeBasedNotifications() throws Exception {
        Fixture fixture = fixture();
        UserDevice first = new UserDevice("first", "first-user", "first-token", "WEB");
        UserDevice second = new UserDevice("second", "second-user", "second-token", "WEB");
        UserDevice third = new UserDevice("third", "third-user", "third-token", "WEB");
        when(fixture.devices.findActiveNearbyBenefitDevices()).thenReturn(List.of(first, second, third));
        when(fixture.history.existsSent("first-user", "first", "TIME_BASED_BENEFIT", null,
                "2026-08-28", "DINNER"))
                .thenReturn(true);
        when(fixture.locations.find("second-user")).thenReturn(Optional.empty());
        when(fixture.locations.find("third-user")).thenThrow(new IllegalStateException("failure"));
        fixture.service.sendTimeBasedBenefitNotifications(TimeSlot.DINNER);
        verify(fixture.fcm, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("탈퇴 시 사용자의 알림 발송 이력을 모두 삭제한다")
    void deletesAllHistoryByUserId() {
        NotificationMapper history = org.mockito.Mockito.mock(NotificationMapper.class);
        service(history, org.mockito.Mockito.mock(DeviceMapper.class), org.mockito.Mockito.mock(FcmService.class),
                Instant.parse("2026-08-28T11:00:00Z")).deleteAllByUserId("user");
        verify(history).deleteHistoryByUserId("user");
    }

    private NotificationService service(NotificationMapper history, DeviceMapper devices, FcmService fcm, Instant now) {
        Clock clock = Clock.fixed(now, ZoneId.of("Asia/Seoul"));
        return new NotificationService(history, devices, fcm, clock,
                org.mockito.Mockito.mock(UserLocationService.class),
                org.mockito.Mockito.mock(MerchantCategoryMapper.class),
                org.mockito.Mockito.mock(MerchantNearbyQueryService.class),
                org.mockito.Mockito.mock(MerchantCardRecommendationService.class));
    }

    private Fixture fixture() {
        return new Fixture();
    }

    private static final class Fixture {
        private final NotificationMapper history = org.mockito.Mockito.mock(NotificationMapper.class);
        private final DeviceMapper devices = org.mockito.Mockito.mock(DeviceMapper.class);
        private final FcmService fcm = org.mockito.Mockito.mock(FcmService.class);
        private final UserLocationService locations = org.mockito.Mockito.mock(UserLocationService.class);
        private final MerchantCategoryMapper categories = org.mockito.Mockito.mock(MerchantCategoryMapper.class);
        private final MerchantNearbyQueryService nearby = org.mockito.Mockito.mock(MerchantNearbyQueryService.class);
        private final MerchantCardRecommendationService recommendations = org.mockito.Mockito.mock(
                MerchantCardRecommendationService.class);
        private final NotificationService service = new NotificationService(history, devices, fcm,
                Clock.fixed(Instant.parse("2026-08-28T11:00:00Z"), ZoneId.of("Asia/Seoul")), locations,
                categories, nearby, recommendations);

        private void performanceCandidate() {
            when(history.findPerformanceDeadlineCandidates("2026-08")).thenReturn(List.of(
                    new PerformanceDeadlineCandidate("user", "card", "카드", new BigDecimal("120000"),
                            new BigDecimal("150000"))));
            when(devices.findActiveByUserId("user")).thenReturn(List.of(
                    new UserDevice("device", "user", "token", "WEB")));
        }

        private void nearbyDevice() {
            when(devices.findActiveNearbyBenefitDevices()).thenReturn(List.of(
                    new UserDevice("device", "user", "token", "WEB")));
        }

        private void eligibleNearbyBenefit() {
            nearbyDevice();
            when(locations.find("user")).thenReturn(Optional.of(new UserLocationService.Location(35.1, 129.1)));
            when(categories.findAllOrderedByDisplayOrder()).thenReturn(List.of(
                    new MerchantCategoryRow("category", "CAFE", "카페", 1)));
            when(nearby.getNearbyMerchants(eq("category"), any(), any(), eq(500), eq(null)))
                    .thenReturn(List.of(new NearbyMerchantResponse(
                            "merchant", "가맹점", 35.1, 129.1, 10, "주소")));
            MerchantCardRecommendationResponse recommendation = org.mockito.Mockito.mock(
                    MerchantCardRecommendationResponse.class);
            when(recommendation.recommendedCard()).thenReturn(
                    org.mockito.Mockito.mock(RankedCardBenefitResponse.class));
            when(recommendations.recommendBatch(eq("user"), any(), eq(null))).thenReturn(
                    new MerchantCardRecommendationBatchResponse(List.of(recommendation)));
        }

        private void claims() {
            when(history.claimPending(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(1);
        }
    }
}
