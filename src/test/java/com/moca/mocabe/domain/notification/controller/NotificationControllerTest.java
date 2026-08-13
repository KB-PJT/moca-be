package com.moca.mocabe.domain.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.notification.dto.DeviceResponse;
import com.moca.mocabe.domain.notification.dto.RegisterDeviceRequest;
import com.moca.mocabe.domain.notification.dto.UpdateLocationRequest;
import com.moca.mocabe.domain.notification.service.DeviceService;
import com.moca.mocabe.domain.notification.service.UserLocationService;
import com.moca.mocabe.domain.notification.type.DeviceType;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("알림 설정 API Controller")
class NotificationControllerTest {
    @Test
    @DisplayName("인증 사용자 ID로 FCM 토큰을 등록한다")
    void registersDeviceForCurrentUser() {
        DeviceService service = org.mockito.Mockito.mock(DeviceService.class);
        CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn("user");
        RegisterDeviceRequest request = new RegisterDeviceRequest();
        request.setFcmToken("token");
        request.setDeviceType(DeviceType.WEB);
        when(service.register("user", request)).thenReturn(new DeviceResponse("device", "WEB", true));

        new DeviceController(service, currentUser).register(request);

        verify(service).register("user", request);
    }

    @Test
    @DisplayName("인증 사용자 ID로 최근 위치를 갱신한다")
    void updatesLocationForCurrentUser() {
        UserLocationService service = org.mockito.Mockito.mock(UserLocationService.class);
        CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn("user");
        UpdateLocationRequest request = new UpdateLocationRequest();
        request.setLatitude(35.1);
        request.setLongitude(129.1);

        new LocationController(service, currentUser).update(request);

        verify(service).update("user", request);
    }
}
