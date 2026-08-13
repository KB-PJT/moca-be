package com.moca.mocabe.domain.notification.controller;

import com.moca.mocabe.domain.notification.dto.DeviceResponse;
import com.moca.mocabe.domain.notification.dto.RegisterDeviceRequest;
import com.moca.mocabe.domain.notification.service.DeviceService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Lazy;

@RestController
@Lazy
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> register(@Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.register(
                currentUserProvider.getCurrentUserId(), request)));
    }

}
