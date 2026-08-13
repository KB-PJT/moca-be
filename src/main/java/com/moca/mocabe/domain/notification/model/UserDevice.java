package com.moca.mocabe.domain.notification.model;

public record UserDevice(String userDeviceId, String userId, String fcmToken, String deviceType) { }
