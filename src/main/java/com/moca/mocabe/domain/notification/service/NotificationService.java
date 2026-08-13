package com.moca.mocabe.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.service.MerchantCardRecommendationService;
import com.moca.mocabe.domain.merchant.service.MerchantNearbyQueryService;
import com.moca.mocabe.domain.notification.mapper.DeviceMapper;
import com.moca.mocabe.domain.notification.mapper.NotificationMapper;
import com.moca.mocabe.domain.notification.model.PerformanceDeadlineCandidate;
import com.moca.mocabe.domain.notification.model.UserDevice;
import com.moca.mocabe.domain.notification.type.TimeSlot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 알림 대상 선별·중복 확인·기기별 발송을 담당한다. */
public class NotificationService {
    private final NotificationMapper mapper;
    private final DeviceMapper deviceMapper;
    private final FcmService fcmService;
    private final Clock clock;
    private final UserLocationService locationService;
    private final MerchantCategoryMapper categoryMapper;
    private final MerchantNearbyQueryService nearbyService;
    private final MerchantCardRecommendationService recommendationService;

    public NotificationService(NotificationMapper mapper, DeviceMapper deviceMapper, FcmService fcmService,
                               Clock clock, UserLocationService locationService,
                               MerchantCategoryMapper categoryMapper, MerchantNearbyQueryService nearbyService,
                               MerchantCardRecommendationService recommendationService) {
        this.mapper = mapper;
        this.deviceMapper = deviceMapper;
        this.fcmService = fcmService;
        this.clock = clock;
        this.locationService = locationService;
        this.categoryMapper = categoryMapper;
        this.nearbyService = nearbyService;
        this.recommendationService = recommendationService;
    }

    public void sendPerformanceDeadlineNotifications() {
        LocalDate today = LocalDate.now(clock);
        if (today.getDayOfMonth() != today.lengthOfMonth() - 3) {
            return;
        }
        List<PerformanceDeadlineCandidate> candidates = mapper.findPerformanceDeadlineCandidates(
                YearMonth.from(today).toString());
        for (PerformanceDeadlineCandidate candidate : candidates) {
            sendPerformanceCandidate(candidate, today);
        }
    }

    public void sendTimeBasedBenefitNotifications(TimeSlot timeSlot) {
        LocalDate today = LocalDate.now(clock);
        for (UserDevice device : deviceMapper.findActiveNearbyBenefitDevices()) {
            sendTimeBasedBenefitNotification(device, timeSlot, today);
        }
    }

    private void sendTimeBasedBenefitNotification(UserDevice device, TimeSlot timeSlot, LocalDate today) {
        try {
            if (mapper.existsSent(device.userId(), device.userDeviceId(), "TIME_BASED_BENEFIT", null,
                    today.toString(), timeSlot.name())) {
                return;
            }
            locationService.find(device.userId()).ifPresent(location -> {
                if (hasEligibleNearbyMerchant(device.userId(), location)) {
                    send(device.userId(), device, "TIME_BASED_BENEFIT", null, timeSlot, today,
                            "지금 결제 전 혜택 확인해보세요", "주변에서 받을 수 있는 카드 혜택이 있어요.",
                            Map.of("type", "TIME_BASED_BENEFIT", "target", "MAP"));
                }
            });
        } catch (Exception exception) {
            // 카카오·Redis·추천 조회 실패는 특정 사용자만 건너뛰고 다음 사용자를 처리한다.
        }
    }

    private void sendPerformanceCandidate(PerformanceDeadlineCandidate candidate, LocalDate today) {
        BigDecimal remaining = candidate.requiredSpendAmount().subtract(candidate.currentSpendAmount());
        for (UserDevice device : deviceMapper.findActiveByUserId(candidate.userId())) {
            if (!mapper.existsSent(candidate.userId(), device.userDeviceId(),
                    "PERFORMANCE_DEADLINE", candidate.userCardId(),
                    today.toString(), null)) {
                send(candidate.userId(), device, "PERFORMANCE_DEADLINE", candidate.userCardId(), null, today,
                        candidate.cardName() + " 실적까지 " + remaining.toPlainString() + "원 남았어요. D-3",
                        "이번 달 실적을 채우면 다음 달 혜택을 받을 수 있어요.",
                        Map.of("type", "PERFORMANCE_DEADLINE", "userCardId", candidate.userCardId()));
            }
        }
    }

    private boolean hasEligibleNearbyMerchant(String userId, UserLocationService.Location location) {
        List<String> merchantIds = new ArrayList<>();
        categoryMapper.findAllOrderedByDisplayOrder().forEach(category -> {
            try {
                nearbyService.getNearbyMerchants(category.merchantCategoryId(), location.latitude(),
                        location.longitude(), 500, null).forEach(merchant -> merchantIds.add(merchant.merchantId()));
            } catch (Exception exception) {
                // 한 카테고리의 외부 장소 검색 실패는 다른 카테고리 결과를 폐기하지 않는다.
            }
        });
        return !merchantIds.isEmpty() && recommendationService.recommendBatch(userId, merchantIds, null)
                .recommendations().stream().anyMatch(item -> item.recommendedCard() != null);
    }

    private void send(String userId, UserDevice device, String type, String referenceId, TimeSlot slot,
                      LocalDate date, String title, String body, Map<String, String> data) {
        String historyId = UUID.randomUUID().toString();
        String deliveryKey = deliveryKey(userId, device.userDeviceId(), type, referenceId, slot, date);
        if (mapper.claimPending(historyId, deliveryKey, userId, device.userDeviceId(), type, referenceId,
                slot == null ? null : slot.name(), date.toString(), title, body, "PENDING") == 0) {
            return;
        }
        try {
            String messageId = fcmService.send(device.fcmToken(), title, body, data);
            mapper.updateHistory(historyId, "SENT", messageId, null);
        } catch (FirebaseMessagingException exception) {
            if (fcmService.isInvalidToken(exception)) {
                deviceMapper.deactivate(device.userDeviceId(), userId);
            }
            mapper.updateHistory(historyId, "FAILED", null,
                    exception.getMessagingErrorCode() == null ? exception.getClass().getSimpleName()
                            : exception.getMessagingErrorCode().name());
        } catch (Exception exception) {
            mapper.updateHistory(historyId, "FAILED", null,
                    exception.getClass().getSimpleName());
        }
    }

    private String deliveryKey(String userId, String deviceId, String type, String referenceId,
                               TimeSlot slot, LocalDate date) {
        String raw = String.join("|", userId, deviceId, type, referenceId == null ? "" : referenceId,
                slot == null ? "" : slot.name(), date.toString());
        return UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
