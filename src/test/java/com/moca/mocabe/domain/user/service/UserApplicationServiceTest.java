package com.moca.mocabe.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.domain.user.dto.LocationSettingsRequest;
import com.moca.mocabe.domain.user.dto.NotificationSettingsRequest;
import com.moca.mocabe.domain.user.dto.UpdateNicknameRequest;
import com.moca.mocabe.domain.user.dto.UpdateCardSortModeRequest;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.dto.WithdrawUserRequest;
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
    @DisplayName("사용자를 찾을 수 없으면 사용자 없음 예외를 반환한다")
    void rejectsMissingUser() {
        when(userDomainService.requireUser(USER_ID)).thenThrow(new UserNotFoundException());

        org.junit.jupiter.api.Assertions.assertThrows(UserNotFoundException.class,
                () -> userApplicationService.getProfile(USER_ID));
    }

    @Test
    @DisplayName("탈퇴 시 사용자 설정을 지운 뒤 계정을 물리 삭제한다")
    void permanentlyDeletesUserData() {
        when(userDomainService.requireUser(USER_ID)).thenReturn(user());
        when(userDomainService.deleteUser(USER_ID)).thenReturn(true);
        WithdrawUserRequest request = new WithdrawUserRequest();
        request.setReason("not_needed");
        request.setConfirmed(true);

        assertTrue(userApplicationService.withdraw(USER_ID, request));
        InOrder inOrder = org.mockito.Mockito.inOrder(opaqueTokenService, userDomainService);
        inOrder.verify(opaqueTokenService).revokeAll(USER_ID);
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
