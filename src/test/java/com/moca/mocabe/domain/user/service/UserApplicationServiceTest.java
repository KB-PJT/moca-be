package com.moca.mocabe.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.service.CodefCredentialStore;
import com.moca.mocabe.domain.notification.service.DeviceService;
import com.moca.mocabe.domain.notification.service.NotificationService;
import com.moca.mocabe.domain.support.service.SupportInquiryService;
import com.moca.mocabe.domain.user.mapper.WithdrawalRequestMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.domain.user.model.NotificationSettings;
import com.moca.mocabe.domain.user.model.LocationSettings;
import com.moca.mocabe.domain.user.dto.LocationSettingsRequest;
import com.moca.mocabe.domain.user.dto.NotificationSettingsRequest;
import com.moca.mocabe.domain.user.dto.UpdateNicknameRequest;
import com.moca.mocabe.domain.user.dto.UpdateCardSortModeRequest;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.dto.WithdrawUserRequest;
import com.moca.mocabe.domain.user.dto.BenefitPreferenceRequest;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private OpaqueTokenService opaqueTokenService;

    @Mock
    private CardQueryService cardQueryService;

    @Mock
    private CodefCredentialStore codefCredentialStore;

    @Mock
    private SupportInquiryService supportInquiryService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private WithdrawalRequestMapper withdrawalRequestMapper;

    @InjectMocks
    private UserApplicationService userApplicationService;

    @Test
    @DisplayName("프로필 조회는 존재하는 사용자만 반환한다")
    void getsProfile() {
        when(userDomainService.requireUser(USER_ID)).thenReturn(user());

        UserProfileResponse response = userApplicationService.getProfile(USER_ID);

        assertEquals(USER_ID, response.getUserId());
        verify(userDomainService).requireUser(USER_ID);
    }

    @Test
    @DisplayName("닉네임 변경은 공백을 제거한 뒤 사용자에게 저장한다")
    void updatesNickname() {
        when(userDomainService.requireUser(USER_ID)).thenReturn(user());
        UpdateNicknameRequest request = new UpdateNicknameRequest();
        request.setNickname("  새 모카  ");

        userApplicationService.updateNickname(USER_ID, request);

        verify(userDomainService).updateNickname(USER_ID, "새 모카");
    }

    @Test
    @DisplayName("카드 정렬 방식 변경은 인증 사용자의 설정으로 저장한다")
    void updatesCardSortMode() {
        when(userDomainService.requireUser(USER_ID)).thenReturn(user());
        UpdateCardSortModeRequest request = new UpdateCardSortModeRequest();
        request.setCardSortMode("MANUAL");

        userApplicationService.updateCardSortMode(USER_ID, request);

        verify(userDomainService).updateCardSortMode(USER_ID, "MANUAL");
    }

    @Test
    @DisplayName("저장된 알림 설정이 없으면 모든 알림을 기본 비활성으로 반환한다")
    void returnsDisabledDefaultNotificationSettings() {
        when(userDomainService.findNotificationSettings(USER_ID)).thenReturn(null);

        assertFalse(userApplicationService.getNotificationSettings(USER_ID).isPerformanceClosingEnabled());
        assertFalse(userApplicationService.getNotificationSettings(USER_ID).isNearbyBenefitEnabled());
        assertFalse(userApplicationService.getNotificationSettings(USER_ID).isBenefitLimitEnabled());
        assertFalse(userApplicationService.getNotificationSettings(USER_ID).isMarketingEnabled());
    }

    @Test
    @DisplayName("알림 설정 변경은 인증 사용자 단위로 upsert한다")
    void updatesNotificationSettings() {
        NotificationSettingsRequest request = new NotificationSettingsRequest();
        request.setPerformanceClosingEnabled(true);
        request.setNearbyBenefitEnabled(true);
        request.setBenefitLimitEnabled(false);
        request.setMarketingEnabled(false);

        userApplicationService.updateNotificationSettings(USER_ID, request);

        ArgumentCaptor<com.moca.mocabe.domain.user.model.NotificationSettings> settingsCaptor =
                ArgumentCaptor.forClass(com.moca.mocabe.domain.user.model.NotificationSettings.class);
        verify(userDomainService).saveNotificationSettings(eq(USER_ID), settingsCaptor.capture());
        assertTrue(settingsCaptor.getValue().isPerformanceClosingEnabled());
        assertTrue(settingsCaptor.getValue().isNearbyBenefitEnabled());
        assertFalse(settingsCaptor.getValue().isBenefitLimitEnabled());
        assertFalse(settingsCaptor.getValue().isMarketingEnabled());
    }

    @Test
    @DisplayName("위치 추천 설정 변경은 인증 사용자의 컬럼을 갱신한다")
    void updatesLocationSettings() {
        LocationSettingsRequest request = new LocationSettingsRequest();
        request.setLocationRecommendationEnabled(true);

        assertTrue(userApplicationService.updateLocationSettings(USER_ID, request)
                .isLocationRecommendationEnabled());
        verify(userDomainService).updateLocationRecommendationEnabled(USER_ID, true);
    }

    @Test
    @DisplayName("저장된 위치 설정이 없으면 위치 추천을 기본 비활성으로 반환한다")
    void returnsDisabledDefaultLocationSettings() {
        when(userDomainService.findLocationSettings(USER_ID)).thenReturn(null);

        assertFalse(userApplicationService.getLocationSettings(USER_ID).isLocationRecommendationEnabled());
    }

    @Test
    @DisplayName("저장된 알림과 위치 설정을 그대로 반환한다")
    void returnsStoredSettings() {
        NotificationSettings notifications = new NotificationSettings();
        notifications.setNearbyBenefitEnabled(true);
        LocationSettings location = new LocationSettings();
        location.setLocationRecommendationEnabled(true);
        when(userDomainService.findNotificationSettings(USER_ID)).thenReturn(notifications);
        when(userDomainService.findLocationSettings(USER_ID)).thenReturn(location);

        assertTrue(userApplicationService.getNotificationSettings(USER_ID).isNearbyBenefitEnabled());
        assertTrue(userApplicationService.getLocationSettings(USER_ID).isLocationRecommendationEnabled());
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 사용자 없음 예외를 반환한다")
    void rejectsMissingUser() {
        when(userDomainService.requireUser(USER_ID)).thenThrow(new UserNotFoundException());

        org.junit.jupiter.api.Assertions.assertThrows(UserNotFoundException.class,
                () -> userApplicationService.getProfile(USER_ID));
    }

    @Test
    @DisplayName("보유 카드가 없으면 신규 사용자로 판단한다")
    void treatsUserWithoutAnyCardAsNewUser() {
        when(cardQueryService.hasAnyCard(USER_ID)).thenReturn(false);

        assertTrue(userApplicationService.isNewUser(USER_ID).isNewUser());
    }

    @Test
    @DisplayName("활성·비활성 상관없이 보유 카드가 있으면 기존 사용자로 판단한다")
    void treatsUserWithAnyCardAsExistingUser() {
        when(cardQueryService.hasAnyCard(USER_ID)).thenReturn(true);

        assertFalse(userApplicationService.isNewUser(USER_ID).isNewUser());
    }

    @Test
    @DisplayName("혜택 선호를 조회하고 온보딩 선택값으로 변경한다")
    void getsAndUpdatesBenefitPreference() {
        when(userDomainService.findBenefitPreferenceType(USER_ID))
                .thenReturn(BenefitPreferenceType.IMMEDIATE_SAVINGS);
        assertEquals(BenefitPreferenceType.IMMEDIATE_SAVINGS,
                userApplicationService.getBenefitPreference(USER_ID).benefitPreferenceType());

        BenefitPreferenceRequest request = new BenefitPreferenceRequest();
        request.setBenefitPreferenceType(BenefitPreferenceType.TRAVEL_MILEAGE);
        assertEquals(BenefitPreferenceType.TRAVEL_MILEAGE,
                userApplicationService.updateBenefitPreference(USER_ID, request).benefitPreferenceType());
        verify(userDomainService).updateBenefitPreferenceType(USER_ID, BenefitPreferenceType.TRAVEL_MILEAGE);
    }

    @Test
    @DisplayName("탈퇴 시 탈퇴 사유를 남기고 연관 데이터를 모두 지운 뒤 계정을 물리 삭제한다")
    void permanentlyDeletesUserData() {
        when(userDomainService.requireUser(USER_ID)).thenReturn(user());
        when(userDomainService.deleteUser(USER_ID)).thenReturn(true);
        WithdrawUserRequest request = new WithdrawUserRequest();
        request.setReason("not_needed");
        request.setReasonDetail("설명");
        request.setConfirmed(true);

        assertTrue(userApplicationService.withdraw(USER_ID, request));

        verify(withdrawalRequestMapper).insertWithdrawalRequest("not_needed", "설명", true);
        InOrder inOrder = org.mockito.Mockito.inOrder(
                withdrawalRequestMapper, opaqueTokenService, cardQueryService,
                codefCredentialStore, supportInquiryService, notificationService, deviceService,
                userDomainService);
        inOrder.verify(withdrawalRequestMapper).insertWithdrawalRequest("not_needed", "설명", true);
        inOrder.verify(opaqueTokenService).revokeAll(USER_ID);
        inOrder.verify(cardQueryService).deleteAllByUserId(USER_ID);
        inOrder.verify(codefCredentialStore).deleteAllByUserId(USER_ID);
        inOrder.verify(supportInquiryService).deleteAllByUserId(USER_ID);
        inOrder.verify(notificationService).deleteAllByUserId(USER_ID);
        inOrder.verify(deviceService).deleteAllByUserId(USER_ID);
        inOrder.verify(userDomainService).deleteUser(USER_ID);
    }

    private UserProfile user() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(USER_ID);
        userProfile.setNickname("모카");
        userProfile.setEmail("moca@example.com");
        userProfile.setUserType("user");
        userProfile.setCardSortMode("AUTO");
        return userProfile;
    }
}
