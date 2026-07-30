package com.moca.mocabe.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserApplicationService userApplicationService;

    @Test
    @DisplayName("프로필 조회는 존재하는 사용자만 반환한다")
    void getsProfile() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());

        UserProfileResponse response = userApplicationService.getProfile(USER_ID);

        assertEquals(USER_ID, response.getUserId());
        verify(userMapper).findProfileById(USER_ID);
    }

    @Test
    @DisplayName("닉네임 변경은 공백을 제거한 뒤 사용자에게 저장한다")
    void updatesNickname() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        UpdateNicknameRequest request = new UpdateNicknameRequest();
        request.setNickname("  새 모카  ");

        userApplicationService.updateNickname(USER_ID, request);

        verify(userMapper).updateNickname(USER_ID, "새 모카");
    }

    @Test
    @DisplayName("카드 정렬 방식 변경은 인증 사용자의 설정으로 저장한다")
    void updatesCardSortMode() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        UpdateCardSortModeRequest request = new UpdateCardSortModeRequest();
        request.setCardSortMode("MANUAL");

        userApplicationService.updateCardSortMode(USER_ID, request);

        verify(userMapper).updateCardSortMode(USER_ID, "MANUAL");
    }

    @Test
    @DisplayName("저장된 알림 설정이 없으면 모든 알림을 기본 비활성으로 반환한다")
    void returnsDisabledDefaultNotificationSettings() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        when(userMapper.findNotificationSettingsByUserId(USER_ID)).thenReturn(null);

        assertFalse(userApplicationService.getNotificationSettings(USER_ID).isNearbyBenefitEnabled());
    }

    @Test
    @DisplayName("알림 설정 변경은 인증 사용자 단위로 upsert한다")
    void updatesNotificationSettings() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        NotificationSettingsRequest request = new NotificationSettingsRequest();
        request.setPerformanceClosingEnabled(true);
        request.setNearbyBenefitEnabled(true);

        userApplicationService.updateNotificationSettings(USER_ID, request);

        verify(userMapper).upsertNotificationSettings(eq(USER_ID), any());
    }

    @Test
    @DisplayName("위치 추천 설정 변경은 인증 사용자의 컬럼을 갱신한다")
    void updatesLocationSettings() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        LocationSettingsRequest request = new LocationSettingsRequest();
        request.setLocationRecommendationEnabled(true);

        assertTrue(userApplicationService.updateLocationSettings(USER_ID, request)
                .isLocationRecommendationEnabled());
        verify(userMapper).updateLocationRecommendationEnabled(USER_ID, true);
    }

    @Test
    @DisplayName("저장된 위치 설정이 없으면 위치 추천을 기본 비활성으로 반환한다")
    void returnsDisabledDefaultLocationSettings() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        when(userMapper.findLocationSettingsByUserId(USER_ID)).thenReturn(null);

        assertFalse(userApplicationService.getLocationSettings(USER_ID).isLocationRecommendationEnabled());
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 사용자 없음 예외를 반환한다")
    void rejectsMissingUser() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(UserNotFoundException.class,
                () -> userApplicationService.getProfile(USER_ID));
    }

    @Test
    @DisplayName("탈퇴 시 사용자 설정을 지운 뒤 계정을 물리 삭제한다")
    void permanentlyDeletesUserData() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(user());
        when(userMapper.deleteUser(USER_ID)).thenReturn(1);
        WithdrawUserRequest request = new WithdrawUserRequest();
        request.setReason("not_needed");
        request.setConfirmed(true);

        assertTrue(userApplicationService.withdraw(USER_ID, request));
        verify(userMapper).deleteNotificationSettings(USER_ID);
        verify(userMapper).deleteUser(USER_ID);
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
