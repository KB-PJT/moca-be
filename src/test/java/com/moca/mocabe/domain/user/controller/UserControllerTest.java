package com.moca.mocabe.domain.user.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.domain.user.dto.UserProfileResponse;
import com.moca.mocabe.domain.user.dto.LocationSettingsRequest;
import com.moca.mocabe.domain.user.dto.LocationSettingsResponse;
import com.moca.mocabe.domain.user.dto.NewUserCheckResponse;
import com.moca.mocabe.domain.user.dto.NotificationSettingsRequest;
import com.moca.mocabe.domain.user.dto.NotificationSettingsResponse;
import com.moca.mocabe.domain.user.dto.UpdateNicknameRequest;
import com.moca.mocabe.domain.user.dto.UpdateCardSortModeRequest;
import com.moca.mocabe.domain.user.dto.WithdrawUserRequest;
import com.moca.mocabe.domain.user.dto.BenefitPreferenceResponse;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import com.moca.mocabe.domain.user.model.LocationSettings;
import com.moca.mocabe.domain.user.model.NotificationSettings;
import com.moca.mocabe.domain.user.service.UserApplicationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.auth.AuthenticationRequiredException;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    private UserApplicationService userApplicationService;
    private CurrentUserProvider currentUserProvider;
    private UserController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userApplicationService = org.mockito.Mockito.mock(UserApplicationService.class);
        currentUserProvider = org.mockito.Mockito.mock(CurrentUserProvider.class);
        controller = new UserController(userApplicationService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("인증 사용자는 자신의 프로필을 조회한다")
    void getsOwnProfile() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userApplicationService.getProfile(USER_ID)).thenReturn(profileResponse());

        String response = mockMvc.perform(get("/me"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"success\":true"));
        assertTrue(response.contains("\"userId\":\"" + USER_ID + "\""));
        assertTrue(new ObjectMapper().readTree(response).path("data").path("nickname").asText().equals("모카"));
    }

    @Test
    @DisplayName("빈 닉네임은 400 응답으로 거절한다")
    void rejectsBlankNickname() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        String response = mockMvc.perform(patch("/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"success\":false"));
        assertTrue(response.contains("\"nickname\""));
    }

    @Test
    @DisplayName("인증 사용자는 카드 정렬 방식을 변경한다")
    void updatesCardSortMode() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userApplicationService.updateCardSortMode(org.mockito.ArgumentMatchers.eq(USER_ID), any()))
                .thenReturn(profileResponse());

        String response = mockMvc.perform(patch("/me/card-sort-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardSortMode\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"success\":true"));
    }

    @Test
    @DisplayName("지원하지 않는 카드 정렬 방식은 400 응답으로 거절한다")
    void rejectsUnsupportedCardSortMode() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        String response = mockMvc.perform(patch("/me/card-sort-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardSortMode\":\"RANDOM\"}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"success\":false"));
        assertTrue(response.contains("cardSortMode"));
    }

    @Test
    @DisplayName("보유 카드가 없는 사용자는 신규 사용자로 응답받는다")
    void getsNewUserStatus() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userApplicationService.isNewUser(USER_ID)).thenReturn(new NewUserCheckResponse(true));

        String response = mockMvc.perform(get("/me/onboarding-status"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"success\":true"));
        assertTrue(response.contains("\"newUser\":true"));
    }

    @Test
    @DisplayName("인증 사용자는 혜택 선호를 조회하고 온보딩 선택값으로 변경한다")
    void getsAndUpdatesBenefitPreference() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userApplicationService.getBenefitPreference(USER_ID)).thenReturn(
                new BenefitPreferenceResponse(BenefitPreferenceType.IMMEDIATE_SAVINGS));
        when(userApplicationService.updateBenefitPreference(
                org.mockito.ArgumentMatchers.eq(USER_ID), any())).thenReturn(
                new BenefitPreferenceResponse(BenefitPreferenceType.TRAVEL_MILEAGE));

        mockMvc.perform(get("/me/benefit-preference"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("IMMEDIATE_SAVINGS")));
        mockMvc.perform(patch("/me/benefit-preference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"benefitPreferenceType\":\"TRAVEL_MILEAGE\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("TRAVEL_MILEAGE")));
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 응답을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new AuthenticationRequiredException());

        String response = mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("\"success\":false"));
    }

    @Test
    @DisplayName("인증 사용자는 닉네임, 알림, 위치 및 카드 정렬 설정을 조회·변경한다")
    void managesUserSettings() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        UpdateNicknameRequest nicknameRequest = new UpdateNicknameRequest();
        nicknameRequest.setNickname("새 모카");
        when(userApplicationService.updateNickname(USER_ID, nicknameRequest)).thenReturn(profileResponse());
        UpdateCardSortModeRequest cardSortModeRequest = new UpdateCardSortModeRequest();
        cardSortModeRequest.setCardSortMode("AUTO");
        when(userApplicationService.updateCardSortMode(USER_ID, cardSortModeRequest)).thenReturn(profileResponse());
        NotificationSettingsResponse notificationResponse = new NotificationSettingsResponse(
                new NotificationSettings());
        NotificationSettingsRequest notificationRequest = new NotificationSettingsRequest();
        when(userApplicationService.getNotificationSettings(USER_ID)).thenReturn(notificationResponse);
        when(userApplicationService.updateNotificationSettings(USER_ID, notificationRequest))
                .thenReturn(notificationResponse);
        LocationSettingsResponse locationResponse = new LocationSettingsResponse(new LocationSettings());
        LocationSettingsRequest locationRequest = new LocationSettingsRequest();
        when(userApplicationService.getLocationSettings(USER_ID)).thenReturn(locationResponse);
        when(userApplicationService.updateLocationSettings(USER_ID, locationRequest)).thenReturn(locationResponse);

        assertTrue(controller.updateNickname(nicknameRequest).getBody().isSuccess());
        assertTrue(controller.updateCardSortMode(cardSortModeRequest).getBody().isSuccess());
        assertTrue(controller.getNotificationSettings().getBody().isSuccess());
        assertTrue(controller.updateNotificationSettings(notificationRequest).getBody().isSuccess());
        assertTrue(controller.getLocationSettings().getBody().isSuccess());
        assertTrue(controller.updateLocationSettings(locationRequest).getBody().isSuccess());
    }

    @Test
    @DisplayName("회원 탈퇴는 사용자 서비스의 세션 폐기와 계정 삭제 결과를 반환한다")
    void withdrawsUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        WithdrawUserRequest request = new WithdrawUserRequest();
        request.setConfirmed(true);
        when(userApplicationService.withdraw(USER_ID, request)).thenReturn(true);

        assertTrue(controller.withdraw(request).getBody().getData().isSuccess());

        verify(userApplicationService).withdraw(USER_ID, request);
    }

    private UserProfileResponse profileResponse() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(USER_ID);
        userProfile.setNickname("모카");
        userProfile.setEmail("moca@example.com");
        userProfile.setUserType("user");
        userProfile.setCardSortMode("AUTO");
        return new UserProfileResponse(userProfile);
    }
}
