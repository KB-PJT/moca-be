package com.moca.mocabe.domain.notification.dto;

import com.moca.mocabe.domain.notification.type.DeviceType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class RegisterDeviceRequest {
    @NotBlank
    private String fcmToken;
    @NotNull
    private DeviceType deviceType;
    public String getFcmToken() {
        return fcmToken;
    }
    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
    public DeviceType getDeviceType() {
        return deviceType;
    }
    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }
}
