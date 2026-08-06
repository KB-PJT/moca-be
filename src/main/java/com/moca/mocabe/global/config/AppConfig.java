package com.moca.mocabe.global.config;

import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.mapper.CardApprovalMapper;
import com.moca.mocabe.domain.codef.mapper.CardPerformanceMapper;
import com.moca.mocabe.domain.codef.service.ApprovalCardMatcher;
import com.moca.mocabe.domain.codef.service.ApprovalIngestStore;
import com.moca.mocabe.domain.codef.service.CardSyncService;
import com.moca.mocabe.domain.codef.service.PerformanceSnapshotStore;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.service.MerchantCategoryQueryService;
import com.moca.mocabe.domain.merchant.service.MerchantLookup;
import com.moca.mocabe.domain.merchant.service.MerchantNameNormalizer;
import com.moca.mocabe.domain.codef.infra.AesGcmEncryptor;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.CodefHttpClient;
import com.moca.mocabe.domain.codef.infra.CredentialHasher;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.infra.JdkCodefHttpClient;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.IssuerMapper;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.domain.codef.service.CardCatalogMatcher;
import com.moca.mocabe.domain.codef.service.CardNameNormalizer;
import com.moca.mocabe.domain.codef.mapper.CardCatalogMapper;
import com.moca.mocabe.domain.codef.mapper.LinkedCardMapper;
import com.moca.mocabe.domain.codef.service.CodefCredentialStore;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.service.UserApplicationService;
import com.moca.mocabe.domain.user.service.UserDomainService;
import com.moca.mocabe.domain.home.service.HomeQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.auth.SecurityContextCurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 애플리케이션 객체의 생성과 의존성 연결을 한 곳에서 관리한다. */
@Configuration
public class AppConfig {

    private static final long DEFAULT_CODEF_CONNECT_TIMEOUT_MS = 3_000L;
    // CODEF(특히 개발계)는 카드사 인증 콜백을 기다려 응답이 느릴 수 있어 응답 대기를 넉넉히 둔다.
    // .env.example 기본값과 일치시킨다. 필요 시 MOCA_CODEF_REQUEST_TIMEOUT_MS 환경변수로 재정의한다.
    private static final long DEFAULT_CODEF_REQUEST_TIMEOUT_MS = 30_000L;

    // CODEF baseUrl/tokenUrl은 환경변수로 주입되므로, 설정 실수(오타·오설정)로 Bearer 토큰이나
    // Basic 자격증명이 CODEF가 아닌 외부 host로 전송되는 것을 막기 위해 host를 고정 허용목록으로 제한한다.
    // 이 목록 자체는 환경변수로 재정의할 수 없게 해야 검증의 의미가 있다.
    private static final Set<String> ALLOWED_CODEF_HOSTS =
            Set.of("development.codef.io", "api.codef.io", "oauth.codef.io");

    @Bean
    public CurrentUserProvider currentUserProvider() {
        return new SecurityContextCurrentUserProvider();
    }

    @Bean
    public CardQueryService cardQueryService(UserCardMapper userCardMapper) {
        return new CardQueryService(userCardMapper);
    }

    @Bean
    public HomeQueryService homeQueryService(UserMapper userMapper, UserCardMapper userCardMapper) {
        return new HomeQueryService(userMapper, userCardMapper);
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
    public Encryptor codefEncryptor(Environment environment) {
        byte[] key = Base64.getDecoder().decode(
                requiredProperty(environment, "MOCA_CREDENTIAL_ENCRYPTION_KEY"));
        return new AesGcmEncryptor(key);
    }

    @Bean
    public CredentialHasher credentialHasher(Environment environment) {
        byte[] key = Base64.getDecoder().decode(
                requiredProperty(environment, "MOCA_CREDENTIAL_HASH_KEY"));
        return new CredentialHasher(key);
    }

    @Bean
    public HttpClient codefJavaHttpClient(Environment environment) {
        return HttpClient.newBuilder()
                .connectTimeout(timeoutProperty(
                        environment, "MOCA_CODEF_CONNECT_TIMEOUT_MS", DEFAULT_CODEF_CONNECT_TIMEOUT_MS))
                // 리다이렉트를 자동으로 따라가면 Authorization 헤더(Bearer/Basic)가 서드파티 host로
                // 그대로 전달될 수 있다. CODEF 호출에는 리다이렉트가 필요하지 않으므로 명시적으로 막는다.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    public CodefHttpClient codefHttpClient(HttpClient codefJavaHttpClient, Environment environment) {
        return new JdkCodefHttpClient(
                codefJavaHttpClient,
                timeoutProperty(environment, "MOCA_CODEF_REQUEST_TIMEOUT_MS", DEFAULT_CODEF_REQUEST_TIMEOUT_MS));
    }

    @Bean
    public CodefClient codefClient(CodefHttpClient codefHttpClient, Environment environment) {
        return new CodefClient(
                codefHttpClient,
                requiredProperty(environment, "MOCA_CODEF_CLIENT_ID"),
                requiredProperty(environment, "MOCA_CODEF_CLIENT_SECRET"),
                requiredProperty(environment, "MOCA_CODEF_PUBLIC_KEY"),
                requiredCodefUrl(environment, "MOCA_CODEF_BASE_URL"),
                requiredCodefUrl(environment, "MOCA_CODEF_TOKEN_URL"));
    }

    @Bean
    public CardLinkService cardLinkService(CodefClient codefClient,
                                           CodefCredentialMapper codefCredentialMapper,
                                           CodefCredentialStore codefCredentialStore,
                                           IssuerMapper issuerMapper,
                                           Encryptor codefEncryptor,
                                           CredentialHasher credentialHasher,
                                           CardCatalogMatcher cardCatalogMatcher,
                                           CardCatalogMapper cardCatalogMapper,
                                           LinkedCardMapper linkedCardMapper) {
        return new CardLinkService(
                codefClient, codefCredentialMapper, codefCredentialStore,
                issuerMapper, codefEncryptor, credentialHasher,
                cardCatalogMatcher, cardCatalogMapper, linkedCardMapper);
    }

    @Bean
    public CodefCredentialStore codefCredentialStore(CodefCredentialMapper codefCredentialMapper,
                                                       LinkedCardMapper linkedCardMapper) {
        return new CodefCredentialStore(codefCredentialMapper, linkedCardMapper);
    }

    @Bean
    public CardNameNormalizer cardNameNormalizer() {
        return new CardNameNormalizer();
    }

    @Bean
    public ApprovalCardMatcher approvalCardMatcher(CardNameNormalizer cardNameNormalizer) {
        return new ApprovalCardMatcher(cardNameNormalizer);
    }

    @Bean
    public MerchantNameNormalizer merchantNameNormalizer() {
        return new MerchantNameNormalizer();
    }

    @Bean
    public MerchantLookup merchantLookup(MerchantMapper merchantMapper,
                                         MerchantNameNormalizer merchantNameNormalizer) {
        return new MerchantLookup(merchantMapper, merchantNameNormalizer);
    }

    @Bean
    public MerchantCategoryQueryService merchantCategoryQueryService(MerchantCategoryMapper merchantCategoryMapper) {
        return new MerchantCategoryQueryService(merchantCategoryMapper);
    }

    @Bean
    public ApprovalIngestStore approvalIngestStore(CardApprovalMapper cardApprovalMapper) {
        return new ApprovalIngestStore(cardApprovalMapper);
    }

    @Bean
    public PerformanceSnapshotStore performanceSnapshotStore(CardPerformanceMapper cardPerformanceMapper) {
        return new PerformanceSnapshotStore(cardPerformanceMapper);
    }

    @Bean
    public CardSyncService cardSyncService(CodefClient codefClient,
                                           CodefCredentialMapper codefCredentialMapper,
                                           CardApprovalMapper cardApprovalMapper,
                                           ApprovalCardMatcher approvalCardMatcher,
                                           MerchantLookup merchantLookup,
                                           ApprovalIngestStore approvalIngestStore,
                                           PerformanceSnapshotStore performanceSnapshotStore,
                                           Encryptor codefEncryptor) {
        return new CardSyncService(codefClient, codefCredentialMapper, cardApprovalMapper,
                approvalCardMatcher, merchantLookup, approvalIngestStore, performanceSnapshotStore, codefEncryptor);
    }

    @Bean
    public CardCatalogMatcher cardCatalogMatcher(CardNameNormalizer cardNameNormalizer) {
        return new CardCatalogMatcher(cardNameNormalizer);
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    private static String requiredProperty(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }

    /**
     * CODEF 호출 URL(baseUrl/tokenUrl) 환경변수를 https + 승인된 CODEF host로 제한한다.
     * Bearer 토큰·Basic 자격증명이 설정 실수로 외부 endpoint에 전송되는 것을 막기 위한 방어다.
     */
    private static String requiredCodefUrl(Environment environment, String name) {
        String value = requiredProperty(environment, name);
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(name + "이(가) 올바른 URL 형식이 아닙니다.", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException(name + "은(는) https URL이어야 합니다.");
        }
        String host = uri.getHost();
        if (host == null || !ALLOWED_CODEF_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    name + "의 host가 승인된 CODEF host가 아닙니다: " + host);
        }
        return value;
    }

    private static Duration timeoutProperty(Environment environment, String name, long defaultValue) {
        long value = environment.getProperty(name, Long.class, defaultValue);
        if (value <= 0) {
            throw new IllegalStateException(name + " 환경변수는 1 이상이어야 합니다.");
        }
        return Duration.ofMillis(value);
    }
}
