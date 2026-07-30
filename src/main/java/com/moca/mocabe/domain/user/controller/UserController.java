package com.moca.mocabe.domain.user.controller;

import com.moca.mocabe.domain.user.dto.LocationSettingsRequest;
import com.moca.mocabe.domain.user.dto.LocationSettingsResponse;
import com.moca.mocabe.domain.user.dto.NotificationSettingsRequest;
import com.moca.mocabe.domain.user.dto.NotificationSettingsResponse;
import com.moca.mocabe.domain.user.dto.SuccessResponse;
import com.moca.mocabe.domain.user.dto.UpdateNicknameRequest;
import com.moca.mocabe.domain.user.dto.UpdateCardSortModeRequest;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.dto.WithdrawUserRequest;
import com.moca.mocabe.domain.user.service.UserApplicationService;
import com.moca.mocabe.domain.auth.service.AuthApplicationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;

import javax.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지의 프로필, 알림, 위치 설정, 탈퇴 API를 제공한다.
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;
    private final AuthApplicationService authApplicationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success(
                userApplicationService.getProfile(currentUserProvider.getCurrentUserId())));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateNickname(
            @Valid @RequestBody UpdateNicknameRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userApplicationService.updateNickname(currentUserProvider.getCurrentUserId(), request)));
    }

    @PatchMapping("/card-sort-mode")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCardSortMode(
            @Valid @RequestBody UpdateCardSortModeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userApplicationService.updateCardSortMode(currentUserProvider.getCurrentUserId(), request)));
    }

    @GetMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings() {
        return ResponseEntity.ok(ApiResponse.success(
                userApplicationService.getNotificationSettings(currentUserProvider.getCurrentUserId())));
    }

    @PatchMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @RequestBody NotificationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userApplicationService.updateNotificationSettings(
                currentUserProvider.getCurrentUserId(), request)));
    }

    @GetMapping("/location-settings")
    public ResponseEntity<ApiResponse<LocationSettingsResponse>> getLocationSettings() {
        return ResponseEntity.ok(ApiResponse.success(
                userApplicationService.getLocationSettings(currentUserProvider.getCurrentUserId())));
    }

    @PatchMapping("/location-settings")
    public ResponseEntity<ApiResponse<LocationSettingsResponse>> updateLocationSettings(
            @RequestBody LocationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userApplicationService.updateLocationSettings(currentUserProvider.getCurrentUserId(), request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<SuccessResponse>> withdraw(
            @Valid @RequestBody WithdrawUserRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        boolean withdrawn = userApplicationService.withdraw(userId, request);
        if (withdrawn) {
            authApplicationService.revokeAllSessions(userId);
        }
        return ResponseEntity.ok(ApiResponse.success(new SuccessResponse(withdrawn)));
    }
}
