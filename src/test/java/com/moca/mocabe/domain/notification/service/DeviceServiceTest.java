package com.moca.mocabe.domain.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.notification.dto.RegisterDeviceRequest;
import com.moca.mocabe.domain.notification.mapper.DeviceMapper;
import com.moca.mocabe.domain.notification.model.UserDevice;
import com.moca.mocabe.domain.notification.type.DeviceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FCM 기기 토큰 서비스")
class DeviceServiceTest {
    @Test
    @DisplayName("처음 등록하는 토큰은 신규 기기로 저장한다")
    void insertsNewDevice() {
        DeviceMapper mapper = org.mockito.Mockito.mock(DeviceMapper.class);
        DeviceService service = new DeviceService(mapper);
        RegisterDeviceRequest request = request();

        var response = service.register("user", request);

        assertTrue(response.active());
        assertEquals("WEB", response.deviceType());
        verify(mapper).insert(any(), eq("user"), eq("token"), eq("WEB"));
    }

    @Test
    @DisplayName("이미 등록된 토큰은 소유자와 기기 유형을 갱신한다")
    void reactivatesExistingDevice() {
        DeviceMapper mapper = org.mockito.Mockito.mock(DeviceMapper.class);
        when(mapper.findByToken("token")).thenReturn(new UserDevice("device", "old", "token", "IOS"));

        var response = new DeviceService(mapper).register("user", request());

        assertEquals("device", response.userDeviceId());
        verify(mapper).activate("device", "user", "WEB");
    }

    @Test
    @DisplayName("기기 비활성화를 Mapper에 위임한다")
    void deactivatesDevice() {
        DeviceMapper mapper = org.mockito.Mockito.mock(DeviceMapper.class);
        new DeviceService(mapper).deactivate("user", "device");
        verify(mapper).deactivate("device", "user");
    }

    private RegisterDeviceRequest request() {
        RegisterDeviceRequest request = new RegisterDeviceRequest();
        request.setFcmToken("token");
        request.setDeviceType(DeviceType.WEB);
        return request;
    }
}
