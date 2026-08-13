package com.moca.mocabe.domain.notification.controller;

import com.moca.mocabe.domain.notification.dto.UpdateLocationRequest;
import com.moca.mocabe.domain.notification.service.UserLocationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.response.ApiResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Lazy;

@RestController
@Lazy
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class LocationController {
    private final UserLocationService locationService;
    private final CurrentUserProvider currentUserProvider;

    @PutMapping("/location")
    public ResponseEntity<ApiResponse<Boolean>> update(@Valid @RequestBody UpdateLocationRequest request) {
        locationService.update(currentUserProvider.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(true));
    }
}
