package com.moca.mocabe.domain.notification.dto;

import com.moca.mocabe.domain.notification.type.DeviceType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class RegisterDeviceRequest {
    @NotBlank
    @Size(max = 2048)
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
