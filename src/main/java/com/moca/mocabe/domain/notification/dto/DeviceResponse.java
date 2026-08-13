package com.moca.mocabe.domain.notification.dto;

import com.moca.mocabe.domain.notification.model.UserDevice;

public record DeviceResponse(String userDeviceId, String deviceType, boolean active) {
    public static DeviceResponse from(UserDevice device) {
        return new DeviceResponse(device.userDeviceId(), device.deviceType(), true);
    }
}
