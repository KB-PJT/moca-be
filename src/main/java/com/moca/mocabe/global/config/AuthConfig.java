package com.moca.mocabe.global.config;

import com.moca.mocabe.domain.auth.service.AuthApplicationService;
import com.moca.mocabe.domain.user.service.UserDomainService;
import com.moca.mocabe.global.auth.GoogleAuthorizationCodeClient;
import com.moca.mocabe.global.auth.GoogleAuthorizationCodeExchanger;
import com.moca.mocabe.global.auth.GoogleOAuthHttpClient;
import com.moca.mocabe.global.auth.JdkGoogleOAuthHttpClient;
import com.moca.mocabe.global.auth.MocaOpaqueAuthenticationFilter;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.auth.OpaqueTokenPolicy;
import com.moca.mocabe.global.auth.RedisOpaqueTokenService;
import com.moca.mocabe.global.auth.RefreshCookiePolicy;
import com.moca.mocabe.global.exception.response.ApiErrorResponseWriter;
import com.moca.mocabe.global.exception.security.JsonAccessDeniedHandler;
import com.moca.mocabe.global.exception.security.JsonAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Google OAuth 서버 교환과 Redis opaque 세션 인증을 구성한다. */
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
    private static final List<String> CORS_ALLOWED_ORIGINS = Arrays.asList(
            "http://localhost:5173", "https://moca-fe-rho.vercel.app");

    /** Swagger 정적 화면과 OpenAPI 계약 파일은 인증 필터를 완전히 우회한다. */
    @Bean
    public WebSecurityCustomizer swaggerWebSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(DOCUMENTATION_MATCHERS);
    }

    @Bean
    public HttpClient googleOAuthJavaHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Bean
    public GoogleOAuthHttpClient googleOAuthHttpClient(
            @Qualifier("googleOAuthJavaHttpClient") HttpClient googleOAuthJavaHttpClient) {
        return new JdkGoogleOAuthHttpClient(googleOAuthJavaHttpClient, Duration.ofSeconds(5));
    }

    @Bean
    public GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger(
            GoogleOAuthHttpClient googleOAuthHttpClient, Environment environment) {
        return new GoogleAuthorizationCodeClient(googleOAuthHttpClient,
                requiredProperty(environment, "MOCA_GOOGLE_CLIENT_ID"),
                requiredProperty(environment, "MOCA_GOOGLE_CLIENT_SECRET"),
                Arrays.asList(requiredProperty(environment, "MOCA_GOOGLE_ALLOWED_REDIRECT_URIS").split(",")),
                Arrays.asList(environment.getProperty("MOCA_GOOGLE_REQUIRED_SCOPES",
                        "openid,https://www.googleapis.com/auth/userinfo.email,"
                                + "https://www.googleapis.com/auth/userinfo.profile").split(",")), new ObjectMapper());
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
        boolean localProfile = environment.matchesProfiles("local", "local-test");
        boolean secure = !localProfile || environment.getProperty("MOCA_REFRESH_COOKIE_SECURE", Boolean.class, false);
        return new RefreshCookiePolicy(secure);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(CORS_ALLOWED_ORIGINS);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public RedisOpaqueTokenService opaqueTokenService(StringRedisTemplate stringRedisTemplate,
                                                       Environment environment, OpaqueTokenPolicy opaqueTokenPolicy) {
        String pepper = environment.getProperty("MOCA_TOKEN_HASH_PEPPER");
        if (pepper == null || pepper.trim().isEmpty()) {
            if (!environment.matchesProfiles("local")) {
                throw new IllegalStateException("local 이외 환경에서는 MOCA_TOKEN_HASH_PEPPER가 필수입니다.");
            }
            pepper = "local-token-hash-pepper";
        }
        return new RedisOpaqueTokenService(stringRedisTemplate, pepper, opaqueTokenPolicy);
    }

    @Bean
    public AuthApplicationService authApplicationService(UserDomainService userDomainService,
            GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger,
            OpaqueTokenService opaqueTokenService) {
        return new AuthApplicationService(userDomainService, googleAuthorizationCodeExchanger, opaqueTokenService);
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

    private String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(propertyName + "는 필수입니다.");
        }
        return value.trim();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OpaqueTokenService opaqueTokenService,
                                                   JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                                   JsonAccessDeniedHandler jsonAccessDeniedHandler,
                                                   ApiErrorResponseWriter errorResponseWriter,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
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
