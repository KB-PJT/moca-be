package com.moca.mocabe.global.config;

import com.moca.mocabe.global.auth.RedisOpaqueTokenService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** local-test 프로필에서만 고정 테스트 access token을 Redis에 등록한다. */
@Configuration
@Profile("local-test")
public class LocalTestAuthConfig {

    @Bean
    public InitializingBean localTestAccessTokenRegistrar(RedisOpaqueTokenService opaqueTokenService,
                                                          Environment environment) {
        return () -> opaqueTokenService.registerLocalTestAccessToken(
                requiredProperty(environment, "MOCA_LOCAL_TEST_ACCESS_TOKEN"),
                requiredProperty(environment, "MOCA_LOCAL_TEST_USER_ID"),
                environment.getProperty("MOCA_LOCAL_TEST_USER_TYPE", "user"));
    }

    private String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(propertyName + "는 local-test 환경에서 필수입니다.");
        }
        return value;
    }
}
