package com.moca.mocabe.global.config;

import com.moca.mocabe.domain.auth.service.AuthApplicationService;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.global.auth.GoogleIdTokenVerifier;
import com.moca.mocabe.global.auth.MocaOpaqueAuthenticationFilter;
import com.moca.mocabe.global.auth.NimbusGoogleIdTokenVerifier;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import com.moca.mocabe.global.auth.RedisOpaqueTokenService;
import com.moca.mocabe.global.auth.RefreshCookiePolicy;
import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import com.moca.mocabe.global.exception.security.JsonAccessDeniedHandler;
import com.moca.mocabe.global.exception.security.JsonAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Google ID Token 검증과 Redis opaque 세션 인증을 구성한다. */
@Configuration
@EnableWebSecurity
public class AuthConfig {

    private static final RequestMatcher[] DOCUMENTATION_MATCHERS = {
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/docs/**"),
            new AntPathRequestMatcher("/swagger-assets/**"),
            new AntPathRequestMatcher("/api-docs/**")
    };

    private static final RequestMatcher[] PUBLIC_MATCHERS = {
            new AntPathRequestMatcher("/api/v1/auth/**"),
            new AntPathRequestMatcher("/api/v1/health"),
            DOCUMENTATION_MATCHERS[0],
            DOCUMENTATION_MATCHERS[1],
            DOCUMENTATION_MATCHERS[2],
            DOCUMENTATION_MATCHERS[3]
    };

    /** Swagger 정적 화면과 OpenAPI 계약 파일은 인증 필터를 완전히 우회한다. */
    @Bean
    public WebSecurityCustomizer swaggerWebSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(DOCUMENTATION_MATCHERS);
    }

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(Environment environment) {
        String clientId = environment.getProperty("MOCA_GOOGLE_CLIENT_ID");
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException("MOCA_GOOGLE_CLIENT_ID는 필수입니다.");
        }
        return new NimbusGoogleIdTokenVerifier(clientId);
    }

    @Bean
    public OpaqueTokenPolicy opaqueTokenPolicy(Environment environment) {
        long accessTokenTtlSeconds = environment.getProperty("MOCA_ACCESS_TOKEN_TTL_SECONDS", Long.class, 1800L);
        long refreshTokenTtlSeconds = environment.getProperty("MOCA_REFRESH_TOKEN_TTL_SECONDS", Long.class,
                1_209_600L);
        return new OpaqueTokenPolicy(accessTokenTtlSeconds, refreshTokenTtlSeconds);
    }

    @Bean
    public RefreshCookiePolicy refreshCookiePolicy(Environment environment) {
        boolean localProfile = "local".equals(environment.getProperty("MOCA_PROFILE", "local"));
        boolean secure = environment.getProperty("MOCA_REFRESH_COOKIE_SECURE", Boolean.class, !localProfile);
        return new RefreshCookiePolicy(secure);
    }

    @Bean
    public OpaqueTokenService opaqueTokenService(StringRedisTemplate stringRedisTemplate,
                                                  Environment environment, OpaqueTokenPolicy opaqueTokenPolicy) {
        String profile = environment.getProperty("MOCA_PROFILE", "local");
        String pepper = environment.getProperty("MOCA_TOKEN_HASH_PEPPER");
        if (pepper == null || pepper.trim().isEmpty()) {
            if (!"local".equals(profile)) {
                throw new IllegalStateException("local 이외 환경에서는 MOCA_TOKEN_HASH_PEPPER가 필수입니다.");
            }
            pepper = "local-token-hash-pepper";
        }
        RedisOpaqueTokenService opaqueTokenService = new RedisOpaqueTokenService(
                stringRedisTemplate, pepper, opaqueTokenPolicy);
        if ("local-test".equals(profile)) {
            opaqueTokenService.registerLocalTestAccessToken(
                    requiredProperty(environment, "MOCA_LOCAL_TEST_ACCESS_TOKEN"),
                    requiredProperty(environment, "MOCA_LOCAL_TEST_USER_ID"),
                    environment.getProperty("MOCA_LOCAL_TEST_USER_TYPE", "user"));
        }
        return opaqueTokenService;
    }

    private String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(propertyName + "는 local-test 환경에서 필수입니다.");
        }
        return value;
    }

    @Bean
    public AuthApplicationService authApplicationService(UserMapper userMapper,
                                                         GoogleIdTokenVerifier googleIdTokenVerifier,
                                                         OpaqueTokenService opaqueTokenService) {
        return new AuthApplicationService(userMapper, googleIdTokenVerifier, opaqueTokenService);
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OpaqueTokenService opaqueTokenService,
                                                   JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                                   JsonAccessDeniedHandler jsonAccessDeniedHandler,
                                                   ApiErrorResponseWriter errorResponseWriter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_MATCHERS)
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler))
                .addFilterBefore(new MocaOpaqueAuthenticationFilter(opaqueTokenService, errorResponseWriter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
