package com.moca.mocabe.domain.merchant.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.auth.service.AuthApplicationService;
import com.moca.mocabe.domain.benefit.service.BenefitHistoryQueryService;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.domain.codef.service.CardSyncService;
import com.moca.mocabe.domain.home.service.HomeQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantNearbyQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantCardRecommendationService;
import com.moca.mocabe.domain.merchant.service.MerchantQueryService;
import com.moca.mocabe.domain.report.service.ReportQueryService;
import com.moca.mocabe.domain.support.service.SupportInquiryService;
import com.moca.mocabe.domain.user.service.UserApplicationService;
import com.moca.mocabe.global.auth.AuthenticatedUser;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.auth.RefreshCookiePolicy;
import com.moca.mocabe.global.auth.SecurityContextCurrentUserProvider;
import com.moca.mocabe.global.config.AuthConfig;
import com.moca.mocabe.global.config.WebMvcConfig;
import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import com.moca.mocabe.global.exception.security.JsonAccessDeniedHandler;
import com.moca.mocabe.global.exception.security.JsonAuthenticationEntryPoint;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 실제 {@link AuthConfig}의 SecurityFilterChain을 붙여 /merchants 경로의 인증 계약을 검증한다.
 * {@link MerchantControllerTest}는 standaloneSetup이라 Security 필터를 타지 않아 401을 검증하지 못한다.
 */
class MerchantAuthenticationContractTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private OpaqueTokenService opaqueTokenService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfig.class, SecurityTestConfig.class);
        context.refresh();
        opaqueTokenService = context.getBean(OpaqueTokenService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("Authorization 헤더 없이 카테고리 목록을 조회하면 401을 반환한다")
    void rejectsUnauthenticatedCategoriesRequest() throws Exception {
        JsonNode response = new ObjectMapper().readTree(mockMvc.perform(get("/api/v1/merchants/categories"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());

        assertEquals("AUTHENTICATION_REQUIRED", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("Authorization 헤더 없이 카테고리별 가맹점 목록을 조회하면 401을 반환한다")
    void rejectsUnauthenticatedMerchantsRequest() throws Exception {
        JsonNode response = new ObjectMapper().readTree(mockMvc.perform(get("/api/v1/merchants")
                        .param("categoryId", "cat-cafe"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());

        assertEquals("AUTHENTICATION_REQUIRED", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("Authorization 헤더 없이 가맹점 카드 추천을 조회하면 401을 반환한다")
    void rejectsUnauthenticatedCardRecommendationRequest() throws Exception {
        JsonNode response = new ObjectMapper().readTree(mockMvc.perform(
                        get("/api/v1/merchants/merchant-1/card-recommendations"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());

        assertEquals("AUTHENTICATION_REQUIRED", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("Authorization 헤더 없이 목록 카드 추천을 조회하면 401을 반환한다")
    void rejectsUnauthenticatedCardRecommendationBatchRequest() throws Exception {
        JsonNode response = new ObjectMapper().readTree(mockMvc.perform(
                        get("/api/v1/merchants/card-recommendations")
                                .param("merchantIds", "merchant-1"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());

        assertEquals("AUTHENTICATION_REQUIRED", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("Authorization 헤더 없이 장소 카드 추천을 조회하면 401을 반환한다")
    void rejectsUnauthenticatedPlaceCardRecommendationRequest() throws Exception {
        JsonNode response = new ObjectMapper().readTree(mockMvc.perform(
                        get("/api/v1/merchants/place-card-recommendations")
                                .param("placeName", "동네카페")
                                .param("categoryGroupCode", "CE7"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());

        assertEquals("AUTHENTICATION_REQUIRED", response.path("error").path("code").asText());
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 카테고리 목록 요청이 Security 필터를 통과한다")
    void allowsAuthenticatedCategoriesRequest() throws Exception {
        when(opaqueTokenService.authenticate("valid-token"))
                .thenReturn(new AuthenticatedUser("01980d6a-5c0c-7aaf-9b85-010203040506", "USER"));

        mockMvc.perform(get("/api/v1/merchants/categories").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    /** MerchantController를 포함한 모든 RestController 의존성 + 실제 AuthConfig SecurityFilterChain 구성이다. */
    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfig {

        @Bean
        public AuthApplicationService authApplicationService() {
            return mock(AuthApplicationService.class);
        }

        @Bean
        public UserApplicationService userApplicationService() {
            return mock(UserApplicationService.class);
        }

        @Bean
        public CardQueryService cardQueryService() {
            return mock(CardQueryService.class);
        }

        @Bean
        public CardLinkService cardLinkService() {
            return mock(CardLinkService.class);
        }

        @Bean
        public HomeQueryService homeQueryService() {
            return mock(HomeQueryService.class);
        }

        @Bean
        public BenefitHistoryQueryService benefitHistoryQueryService() {
            return mock(BenefitHistoryQueryService.class);
        }

        @Bean
        public ReportQueryService reportQueryService() {
            return mock(ReportQueryService.class);
        }

        @Bean
        public CardSyncService cardSyncService() {
            return mock(CardSyncService.class);
        }

        @Bean
        public MerchantCategoryQueryService merchantCategoryQueryService() {
            return mock(MerchantCategoryQueryService.class);
        }

        @Bean
        public MerchantQueryService merchantQueryService() {
            return mock(MerchantQueryService.class);
        }

        @Bean
        public MerchantNearbyQueryService merchantNearbyQueryService() {
            return mock(MerchantNearbyQueryService.class);
        }

        @Bean
        public SupportInquiryService supportInquiryService() {
            return mock(SupportInquiryService.class);
        }

        @Bean
        public MerchantCardRecommendationService merchantCardRecommendationService() {
            return mock(MerchantCardRecommendationService.class);
        }

        @Bean
        public CurrentUserProvider currentUserProvider() {
            return new SecurityContextCurrentUserProvider();
        }

        @Bean
        public OpaqueTokenPolicy opaqueTokenPolicy() {
            return new OpaqueTokenPolicy(1_800, 1_209_600);
        }

        @Bean
        public RefreshCookiePolicy refreshCookiePolicy() {
            return new RefreshCookiePolicy(false);
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
        public JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter) {
            return new JsonAuthenticationEntryPoint(errorResponseWriter);
        }

        @Bean
        public JsonAccessDeniedHandler jsonAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter) {
            return new JsonAccessDeniedHandler(errorResponseWriter);
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of("http://localhost:5173"));
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
            configuration.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", configuration);
            return source;
        }

        /** AuthConfig의 실제 규칙(공개 경로 제외 전부 인증 필요)을 그대로 재사용한다. */
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                        OpaqueTokenService opaqueTokenService,
                                                        JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                                        JsonAccessDeniedHandler jsonAccessDeniedHandler,
                                                        ApiErrorResponseWriter errorResponseWriter,
                                                        CorsConfigurationSource corsConfigurationSource)
                throws Exception {
            return new AuthConfig().securityFilterChain(http, opaqueTokenService, jsonAuthenticationEntryPoint,
                    jsonAccessDeniedHandler, errorResponseWriter, corsConfigurationSource);
        }
    }
}
