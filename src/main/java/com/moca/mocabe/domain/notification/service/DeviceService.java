package com.moca.mocabe.domain.notification.service;

import com.moca.mocabe.domain.notification.dto.DeviceResponse;
import com.moca.mocabe.domain.notification.dto.RegisterDeviceRequest;
import com.moca.mocabe.domain.notification.mapper.DeviceMapper;
import com.moca.mocabe.domain.notification.model.UserDevice;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class DeviceService {
    private final DeviceMapper mapper;
    public DeviceService(DeviceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public DeviceResponse register(String userId, RegisterDeviceRequest request) {
        UserDevice existing = mapper.findByToken(request.getFcmToken());
        if (existing == null) {
            String id = UUID.randomUUID().toString();
            mapper.insert(id, userId, request.getFcmToken(), request.getDeviceType().name());
            return new DeviceResponse(id, request.getDeviceType().name(), true);
        }
        mapper.activate(existing.userDeviceId(), userId, request.getDeviceType().name());
        return new DeviceResponse(existing.userDeviceId(), request.getDeviceType().name(), true);
    }

    @Transactional
    public void deactivate(String userId, String deviceId) {
        mapper.deactivate(deviceId, userId);
    }

    @Transactional
    public void deactivateByToken(String userId, String fcmToken) {
        UserDevice device = mapper.findByToken(fcmToken);
        if (device != null && device.userId().equals(userId)) {
            mapper.deactivate(device.userDeviceId(), userId);
        }
    }

    @Transactional
    public void deleteAllByUserId(String userId) {
        mapper.deleteByUserId(userId);
    }
}
