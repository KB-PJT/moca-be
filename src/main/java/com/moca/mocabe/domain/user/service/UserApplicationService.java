package com.moca.mocabe.domain.user.service;

import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.service.CodefCredentialStore;
import com.moca.mocabe.domain.support.service.SupportInquiryService;
import com.moca.mocabe.domain.user.mapper.WithdrawalRequestMapper;
import com.moca.mocabe.domain.user.model.LocationSettings;
import com.moca.mocabe.domain.user.model.NotificationSettings;
import com.moca.mocabe.domain.user.dto.LocationSettingsRequest;
import com.moca.mocabe.domain.user.dto.LocationSettingsResponse;
import com.moca.mocabe.domain.user.dto.NewUserCheckResponse;
import com.moca.mocabe.domain.user.dto.NotificationSettingsRequest;
import com.moca.mocabe.domain.user.dto.NotificationSettingsResponse;
import com.moca.mocabe.domain.user.dto.UpdateNicknameRequest;
import com.moca.mocabe.domain.user.dto.UpdateCardSortModeRequest;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.dto.WithdrawUserRequest;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 프로필과 설정 변경 유스케이스를 담당한다. */
public class UserApplicationService {

    private final UserDomainService userDomainService;
    private final OpaqueTokenService opaqueTokenService;
    private final CardQueryService cardQueryService;
    private final CodefCredentialStore codefCredentialStore;
    private final SupportInquiryService supportInquiryService;
    private final WithdrawalRequestMapper withdrawalRequestMapper;

    public UserApplicationService(UserDomainService userDomainService, OpaqueTokenService opaqueTokenService,
                                  CardQueryService cardQueryService, CodefCredentialStore codefCredentialStore,
                                  SupportInquiryService supportInquiryService,
                                  WithdrawalRequestMapper withdrawalRequestMapper) {
        this.userDomainService = userDomainService;
        this.opaqueTokenService = opaqueTokenService;
        this.cardQueryService = cardQueryService;
        this.codefCredentialStore = codefCredentialStore;
        this.supportInquiryService = supportInquiryService;
        this.withdrawalRequestMapper = withdrawalRequestMapper;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String userId) {
        return new UserProfileResponse(userDomainService.requireUser(userId));
    }

    @Transactional
    public UserProfileResponse updateNickname(String userId, UpdateNicknameRequest request) {
        userDomainService.updateNickname(userId, request.getNickname().trim());
        return getProfile(userId);
    }

    @Transactional
    public UserProfileResponse updateCardSortMode(String userId, UpdateCardSortModeRequest request) {
        userDomainService.updateCardSortMode(userId, request.getCardSortMode());
        return getProfile(userId);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getNotificationSettings(String userId) {
        NotificationSettings settings = userDomainService.findNotificationSettings(userId);
        return new NotificationSettingsResponse(settings == null ? new NotificationSettings() : settings);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(
            String userId, NotificationSettingsRequest request) {
        NotificationSettings settings = new NotificationSettings();
        settings.setPerformanceClosingEnabled(request.getPerformanceClosingEnabled());
        settings.setNearbyBenefitEnabled(request.getNearbyBenefitEnabled());
        settings.setBenefitLimitEnabled(request.getBenefitLimitEnabled());
        settings.setMarketingEnabled(request.getMarketingEnabled());
        userDomainService.saveNotificationSettings(userId, settings);
        return new NotificationSettingsResponse(settings);
    }

    @Transactional(readOnly = true)
    public LocationSettingsResponse getLocationSettings(String userId) {
        LocationSettings settings = userDomainService.findLocationSettings(userId);
        return new LocationSettingsResponse(settings == null ? new LocationSettings() : settings);
    }

    @Transactional
    public LocationSettingsResponse updateLocationSettings(String userId, LocationSettingsRequest request) {
        LocationSettings settings = new LocationSettings();
        settings.setLocationRecommendationEnabled(request.isLocationRecommendationEnabled());
        userDomainService.updateLocationRecommendationEnabled(userId, settings.isLocationRecommendationEnabled());
        return new LocationSettingsResponse(settings);
    }

    @Transactional(readOnly = true)
    public NewUserCheckResponse isNewUser(String userId) {
        boolean hasAnyCard = cardQueryService.hasAnyCard(userId);
        return new NewUserCheckResponse(!hasAnyCard);
    }

    @Transactional
    public boolean withdraw(String userId, WithdrawUserRequest request) {
        userDomainService.requireUser(userId);
        withdrawalRequestMapper.insertWithdrawalRequest(
                request.getReason(), request.getReasonDetail(), request.isConfirmed());
        opaqueTokenService.revokeAll(userId);
        // user_cards, codef_account_credentials, support_inquiries가 users를 참조하므로
        // 자식 데이터부터 정리한 뒤 마지막에 users를 삭제한다.
        cardQueryService.deleteAllByUserId(userId);
        codefCredentialStore.deleteAllByUserId(userId);
        supportInquiryService.deleteAllByUserId(userId);
        return userDomainService.deleteUser(userId);
    }
}
