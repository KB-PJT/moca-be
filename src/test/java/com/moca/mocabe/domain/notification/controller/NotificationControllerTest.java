package com.moca.mocabe.domain.notification.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moca.mocabe.domain.notification.dto.DeviceResponse;
import com.moca.mocabe.domain.notification.dto.RegisterDeviceRequest;
import com.moca.mocabe.domain.notification.service.DeviceService;
import com.moca.mocabe.domain.notification.service.UserLocationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("알림 관리 API")
class NotificationControllerTest {
    private DeviceService deviceService;
    private UserLocationService locationService;
    private CurrentUserProvider currentUser;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deviceService = org.mockito.Mockito.mock(DeviceService.class);
        locationService = org.mockito.Mockito.mock(UserLocationService.class);
        currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DeviceController(deviceService, currentUser),
                        new LocationController(locationService, currentUser))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("FCM 토큰 등록은 요청 사용자 ID가 아닌 인증 사용자 ID를 사용한다")
    void registersDeviceForCurrentUser() throws Exception {
        when(currentUser.getCurrentUserId()).thenReturn("user");
        when(deviceService.register(org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.any(RegisterDeviceRequest.class)))
                .thenReturn(new DeviceResponse("device", "WEB", true));

        mockMvc.perform(post("/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token\",\"deviceType\":\"WEB\",\"userId\":\"attacker\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("\"userDeviceId\":\"device\"")));

        verify(deviceService).register(org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.any(RegisterDeviceRequest.class));
    }

    @Test
    @DisplayName("빈 토큰과 지원하지 않는 기기 유형은 400으로 거절한다")
    void rejectsInvalidDeviceRequest() throws Exception {
        mockMvc.perform(post("/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"\",\"deviceType\":\"WATCH\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DB 허용 길이를 넘는 FCM 토큰은 400으로 거절한다")
    void rejectsTooLongFcmToken() throws Exception {
        String token = "a".repeat(2049);

        mockMvc.perform(post("/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"" + token + "\",\"deviceType\":\"WEB\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("공백으로만 구성된 FCM 토큰은 400으로 거절한다")
    void rejectsWhitespaceOnlyFcmToken() throws Exception {
        mockMvc.perform(post("/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"   \",\"deviceType\":\"WEB\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("최근 위치 갱신은 인증 사용자 ID를 사용한다")
    void updatesLocationForCurrentUser() throws Exception {
        when(currentUser.getCurrentUserId()).thenReturn("user");

        mockMvc.perform(put("/users/me/location").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":35.1,\"longitude\":129.1,\"userId\":\"attacker\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("\"data\":true")));

        verify(locationService).update(org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("위도와 경도가 허용 범위를 벗어나면 400으로 거절한다")
    void rejectsOutOfRangeLocation() throws Exception {
        mockMvc.perform(put("/users/me/location").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":90.1,\"longitude\":-180.1}"))
                .andExpect(status().isBadRequest());
    }
}
