package com.moca.mocabe.domain.notification.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.notification.dto.DeviceResponse;
import com.moca.mocabe.domain.notification.service.DeviceService;
import com.moca.mocabe.domain.notification.service.UserLocationService;
import com.moca.mocabe.global.auth.AuthenticatedUser;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.auth.SecurityContextCurrentUserProvider;
import com.moca.mocabe.global.config.AuthConfig;
import com.moca.mocabe.global.exception.auth.InvalidOpaqueTokenException;
import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import com.moca.mocabe.global.exception.security.JsonAccessDeniedHandler;
import com.moca.mocabe.global.exception.security.JsonAuthenticationEntryPoint;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@DisplayName("알림 API 인증 계약")
class NotificationAuthenticationContractTest {
    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private OpaqueTokenService tokenService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityTestConfig.class);
        context.refresh();
        tokenService = context.getBean(OpaqueTokenService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("인증 토큰 없이 디바이스를 등록하면 401을 반환한다")
    void rejectsUnauthenticatedDeviceRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token\",\"deviceType\":\"WEB\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 토큰 없이 최근 위치를 갱신하면 401을 반환한다")
    void rejectsUnauthenticatedLocationUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/location").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":35.1,\"longitude\":129.1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효하지 않은 Bearer 토큰은 두 알림 API에서 INVALID_TOKEN 401을 반환한다")
    void rejectsInvalidBearerToken() throws Exception {
        when(tokenService.authenticate("invalid-token")).thenThrow(new InvalidOpaqueTokenException());

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token\",\"deviceType\":\"WEB\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("\"code\":\"INVALID_TOKEN\"")));
        mockMvc.perform(put("/api/v1/users/me/location")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":35.1,\"longitude\":129.1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("\"code\":\"INVALID_TOKEN\"")));
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 디바이스 등록 요청을 처리한다")
    void acceptsAuthenticatedDeviceRegistration() throws Exception {
        when(tokenService.authenticate("valid-token")).thenReturn(new AuthenticatedUser(USER_ID, "USER"));
        DeviceService deviceService = context.getBean(DeviceService.class);
        when(deviceService.register(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DeviceResponse("device", "WEB", true));

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"token\",\"deviceType\":\"WEB\"}"))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class SecurityTestConfig implements WebMvcConfigurer {
        @Override
        public void configurePathMatch(PathMatchConfigurer configurer) {
            configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forAnnotation(RestController.class));
        }

        @Bean
        public DeviceController deviceController(DeviceService deviceService, CurrentUserProvider currentUser) {
            return new DeviceController(deviceService, currentUser);
        }

        @Bean
        public LocationController locationController(UserLocationService locationService,
                                                     CurrentUserProvider currentUser) {
            return new LocationController(locationService, currentUser);
        }

        @Bean
        public DeviceService deviceService() {
            return mock(DeviceService.class);
        }

        @Bean
        public UserLocationService userLocationService() {
            return mock(UserLocationService.class);
        }

        @Bean
        public CurrentUserProvider currentUserProvider() {
            return new SecurityContextCurrentUserProvider();
        }

        @Bean
        public OpaqueTokenService opaqueTokenService() {
            return mock(OpaqueTokenService.class);
        }

        @Bean
        public ApiErrorResponseWriter apiErrorResponseWriter() {
            return new ApiErrorResponseWriter(new ObjectMapper());
        }

        @Bean
        public JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint(ApiErrorResponseWriter writer) {
            return new JsonAuthenticationEntryPoint(writer);
        }

        @Bean
        public JsonAccessDeniedHandler jsonAccessDeniedHandler(ApiErrorResponseWriter writer) {
            return new JsonAccessDeniedHandler(writer);
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of("http://localhost:5173"));
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            configuration.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", configuration);
            return source;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueTokenService opaqueTokenService,
                                                       JsonAuthenticationEntryPoint authenticationEntryPoint,
                                                       JsonAccessDeniedHandler accessDeniedHandler,
                                                       ApiErrorResponseWriter errorResponseWriter,
                                                       CorsConfigurationSource corsConfigurationSource)
                throws Exception {
            return new AuthConfig().securityFilterChain(http, opaqueTokenService, authenticationEntryPoint,
                    accessDeniedHandler, errorResponseWriter, corsConfigurationSource);
        }
    }
}
