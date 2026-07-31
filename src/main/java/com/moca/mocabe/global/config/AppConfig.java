package com.moca.mocabe.global.config;

import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.service.UserDomainService;
import com.moca.mocabe.domain.user.service.UserApplicationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.auth.SecurityContextCurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 객체의 생성과 의존성 연결을 한 곳에서 관리한다.
 *
 * <p>Controller와 Application Service는 컴포넌트 스캔으로 등록하지 않고 이 설정에서 명시적으로 조립한다.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public CurrentUserProvider currentUserProvider() {
        return new SecurityContextCurrentUserProvider();
    }

    @Bean
    public CardQueryService cardQueryService(UserCardMapper userCardMapper) {
        return new CardQueryService(userCardMapper);
    }

    @Bean
    public UserDomainService userDomainService(UserMapper userMapper) {
        return new UserDomainService(userMapper);
    }

    @Bean
    public UserApplicationService userApplicationService(UserDomainService userDomainService,
                                                         OpaqueTokenService opaqueTokenService) {
        return new UserApplicationService(userDomainService, opaqueTokenService);
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
