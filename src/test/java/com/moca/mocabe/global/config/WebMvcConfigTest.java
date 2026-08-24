package com.moca.mocabe.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moca.mocabe.domain.auth.service.AuthApplicationService;
import com.moca.mocabe.domain.benefit.service.BenefitHistoryQueryService;
import com.moca.mocabe.domain.benefit.service.BenefitUsageCalculationService;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.domain.home.service.HomeQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantNearbyQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantCardRecommendationService;
import com.moca.mocabe.domain.merchant.service.MerchantQueryService;
import com.moca.mocabe.domain.notification.service.DeviceService;
import com.moca.mocabe.domain.notification.service.UserLocationService;
import com.moca.mocabe.domain.report.service.ReportQueryService;
import com.moca.mocabe.domain.support.service.SupportInquiryService;
import com.moca.mocabe.domain.codef.service.CardSyncService;
import com.moca.mocabe.domain.user.service.UserApplicationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import com.moca.mocabe.global.auth.RefreshCookiePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class WebMvcConfigTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfig.class, WebMvcTestConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void exposesHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertEquals("{\"status\":\"UP\"}",
                        result.getResponse().getContentAsString()));
    }

    @Test
    void allowsConfiguredFrontendOriginsForApiRequests() throws Exception {
        mockMvc.perform(options("/api/v1/auth/google/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("http://localhost:5173",
                        result.getResponse().getHeader("Access-Control-Allow-Origin")))
                .andExpect(result -> assertEquals("true",
                        result.getResponse().getHeader("Access-Control-Allow-Credentials")));
        mockMvc.perform(options("/api/v1/auth/google/login")
                        .header("Origin", "https://moca-fe-rho.vercel.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("https://moca-fe-rho.vercel.app",
                        result.getResponse().getHeader("Access-Control-Allow-Origin")));
        mockMvc.perform(options("/api/v1/users/me/location")
                        .header("Origin", "https://moca-fe-rho.vercel.app")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse()
                        .getHeader("Access-Control-Allow-Methods").contains("PUT")));
    }

    @Test
    void redirectsToProjectSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void servesSwaggerUiFromDocsAlias() throws Exception {
        mockMvc.perform(get("/docs/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("url: \"../api-docs/openapi.yaml\"")));
    }

    @Test
    void servesSwaggerUiConfiguredForMocaSpecification() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("url: \"../api-docs/openapi.yaml\"")));
    }

    @Test
    void servesSwaggerUiAssetsFromWebJar() throws Exception {
        mockMvc.perform(get("/swagger-assets/swagger-ui.css"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains(".swagger-ui")));
    }

    @Test
    void servesOpenApiSpecification() throws Exception {
        mockMvc.perform(get("/api-docs/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("openapi: 3.0.3")))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("/health:")))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("MocaBearerAuth:")))
                .andExpect(result -> {
                    String specification = result.getResponse().getContentAsString();
                    int cardLinkOperation = specification.indexOf("operationId: createCardLink");
                    int cardLinkRequestBody = specification.indexOf("requestBody:", cardLinkOperation);
                    String cardLinkSecurity = specification.substring(cardLinkOperation, cardLinkRequestBody);
                    assertTrue(cardLinkSecurity.contains("- MocaBearerAuth: []"));
                });
    }

    /** MVC·Swagger 단위 테스트에 필요한 Controller 의존성만 Mock으로 제공한다. */
    @Configuration
    static class WebMvcTestConfig {

        @Bean
        public AuthApplicationService authApplicationService() {
            return org.mockito.Mockito.mock(AuthApplicationService.class);
        }

        @Bean
        public UserApplicationService userApplicationService() {
            return org.mockito.Mockito.mock(UserApplicationService.class);
        }

        @Bean
        public CardQueryService cardQueryService() {
            return org.mockito.Mockito.mock(CardQueryService.class);
        }

        @Bean
        public CardLinkService cardLinkService() {
            return org.mockito.Mockito.mock(CardLinkService.class);
        }

        @Bean
        public HomeQueryService homeQueryService() {
            return org.mockito.Mockito.mock(HomeQueryService.class);
        }

        @Bean
        public BenefitHistoryQueryService benefitHistoryQueryService() {
            return org.mockito.Mockito.mock(BenefitHistoryQueryService.class);
        }

        @Bean
        public BenefitUsageCalculationService benefitUsageCalculationService() {
            return org.mockito.Mockito.mock(BenefitUsageCalculationService.class);
        }

        @Bean
        public ReportQueryService reportQueryService() {
            return org.mockito.Mockito.mock(ReportQueryService.class);
        }

        @Bean
        public CardSyncService cardSyncService() {
            return org.mockito.Mockito.mock(CardSyncService.class);
        }

        @Bean
        public MerchantCategoryQueryService merchantCategoryQueryService() {
            return org.mockito.Mockito.mock(MerchantCategoryQueryService.class);
        }

        @Bean
        public MerchantQueryService merchantQueryService() {
            return org.mockito.Mockito.mock(MerchantQueryService.class);
        }

        @Bean
        public MerchantNearbyQueryService merchantNearbyQueryService() {
            return org.mockito.Mockito.mock(MerchantNearbyQueryService.class);
        }

        @Bean
        public SupportInquiryService supportInquiryService() {
            return org.mockito.Mockito.mock(SupportInquiryService.class);
        }

        @Bean
        public MerchantCardRecommendationService merchantCardRecommendationService() {
            return org.mockito.Mockito.mock(MerchantCardRecommendationService.class);
        }

        @Bean
        public DeviceService deviceService() {
            return org.mockito.Mockito.mock(DeviceService.class);
        }

        @Bean
        public UserLocationService userLocationService() {
            return org.mockito.Mockito.mock(UserLocationService.class);
        }

        @Bean
        public CurrentUserProvider currentUserProvider() {
            return org.mockito.Mockito.mock(CurrentUserProvider.class);
        }

        @Bean
        public OpaqueTokenPolicy opaqueTokenPolicy() {
            return new OpaqueTokenPolicy(1_800, 1_209_600);
        }

        @Bean
        public RefreshCookiePolicy refreshCookiePolicy() {
            return new RefreshCookiePolicy(false);
        }
    }
}
