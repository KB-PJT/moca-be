package com.moca.mocabe.domain.user.service;

import com.moca.mocabe.domain.user.model.LocationSettings;
import com.moca.mocabe.domain.user.model.NotificationSettings;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.domain.user.dto.LocationSettingsRequest;
import com.moca.mocabe.domain.user.dto.LocationSettingsResponse;
import com.moca.mocabe.domain.user.dto.NotificationSettingsRequest;
import com.moca.mocabe.domain.user.dto.NotificationSettingsResponse;
import com.moca.mocabe.domain.user.dto.UpdateNicknameRequest;
import com.moca.mocabe.domain.user.dto.UpdateCardSortModeRequest;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.dto.WithdrawUserRequest;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 프로필과 설정 변경 유스케이스를 담당한다. */
public class UserApplicationService {

    private final UserMapper userMapper;

    public UserApplicationService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String userId) {
        return new UserProfileResponse(requireUser(userId));
    }

    @Transactional
    public UserProfileResponse updateNickname(String userId, UpdateNicknameRequest request) {
        requireUser(userId);
        userMapper.updateNickname(userId, request.getNickname().trim());
        return getProfile(userId);
    }

    @Transactional
    public UserProfileResponse updateCardSortMode(String userId, UpdateCardSortModeRequest request) {
        requireUser(userId);
        userMapper.updateCardSortMode(userId, request.getCardSortMode());
        return getProfile(userId);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getNotificationSettings(String userId) {
        requireUser(userId);
        NotificationSettings settings = userMapper.findNotificationSettingsByUserId(userId);
        return new NotificationSettingsResponse(settings == null ? new NotificationSettings() : settings);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(
            String userId, NotificationSettingsRequest request) {
        requireUser(userId);
        NotificationSettings settings = new NotificationSettings();
        settings.setPerformanceClosingEnabled(request.isPerformanceClosingEnabled());
        settings.setNearbyBenefitEnabled(request.isNearbyBenefitEnabled());
        settings.setBenefitLimitEnabled(request.isBenefitLimitEnabled());
        settings.setMarketingEnabled(request.isMarketingEnabled());
        userMapper.upsertNotificationSettings(userId, settings);
        return new NotificationSettingsResponse(settings);
    }

    @Transactional(readOnly = true)
    public LocationSettingsResponse getLocationSettings(String userId) {
        requireUser(userId);
        LocationSettings settings = userMapper.findLocationSettingsByUserId(userId);
        return new LocationSettingsResponse(settings == null ? new LocationSettings() : settings);
    }

    @Transactional
    public LocationSettingsResponse updateLocationSettings(String userId, LocationSettingsRequest request) {
        requireUser(userId);
        LocationSettings settings = new LocationSettings();
        settings.setLocationRecommendationEnabled(request.isLocationRecommendationEnabled());
        userMapper.updateLocationRecommendationEnabled(userId, settings.isLocationRecommendationEnabled());
        return new LocationSettingsResponse(settings);
    }

    @Transactional
    public boolean withdraw(String userId, WithdrawUserRequest request) {
        requireUser(userId);
        userMapper.deleteNotificationSettings(userId);
        return userMapper.deleteUser(userId) == 1;
    }

    private UserProfile requireUser(String userId) {
        UserProfile userProfile = userMapper.findProfileById(userId);
        if (userProfile == null) {
            throw new UserNotFoundException();
        }
        return userProfile;
    }
}
