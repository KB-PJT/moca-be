package com.moca.mocabe.global.config;

import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.service.CardQueryService;
import com.moca.mocabe.domain.codef.infra.AesGcmEncryptor;
import com.moca.mocabe.domain.codef.infra.CodefClient;
import com.moca.mocabe.domain.codef.infra.CredentialFingerprintGenerator;
import com.moca.mocabe.domain.codef.infra.Encryptor;
import com.moca.mocabe.domain.codef.mapper.CodefCredentialMapper;
import com.moca.mocabe.domain.codef.mapper.IssuerMapper;
import com.moca.mocabe.domain.codef.service.CardLinkService;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.service.UserDomainService;
import com.moca.mocabe.domain.user.service.UserApplicationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import com.moca.mocabe.global.auth.OpaqueTokenService;
import com.moca.mocabe.global.auth.SecurityContextCurrentUserProvider;
import com.moca.mocabe.global.exception.GlobalExceptionHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 애플리케이션 객체의 생성과 의존성 연결을 한 곳에서 관리한다.
 *
 * <p>Controller와 Application Service는 컴포넌트 스캔으로 등록하지 않고 이 설정에서 명시적으로 조립한다.</p>
 */
@Configuration
public class AppConfig {

    private static final String DEFAULT_CODEF_BASE_URL = "https://development.codef.io";
    private static final String DEFAULT_CODEF_TOKEN_URL = "https://oauth.codef.io/oauth/token";

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
    public Encryptor codefEncryptor(Environment environment) {
        byte[] key = Base64.getDecoder().decode(
                requiredProperty(environment, "MOCA_CREDENTIAL_ENCRYPTION_KEY"));
        return new AesGcmEncryptor(key);
    }

    @Bean
    public CredentialFingerprintGenerator credentialFingerprintGenerator(Environment environment) {
        byte[] key = Base64.getDecoder().decode(
                requiredProperty(environment, "MOCA_CREDENTIAL_FINGERPRINT_KEY"));
        return new CredentialFingerprintGenerator(key);
    }

    @Bean
    public CodefClient codefClient(Environment environment) {
        return new CodefClient(
                AppConfig::sendPost,
                requiredProperty(environment, "MOCA_CODEF_CLIENT_ID"),
                requiredProperty(environment, "MOCA_CODEF_CLIENT_SECRET"),
                requiredProperty(environment, "MOCA_CODEF_PUBLIC_KEY"),
                environment.getProperty("MOCA_CODEF_BASE_URL", DEFAULT_CODEF_BASE_URL),
                environment.getProperty("MOCA_CODEF_TOKEN_URL", DEFAULT_CODEF_TOKEN_URL));
    }

    @Bean
    public CardLinkService cardLinkService(CodefClient codefClient,
                                           CodefCredentialMapper codefCredentialMapper,
                                           IssuerMapper issuerMapper,
                                           Encryptor codefEncryptor,
                                           CredentialFingerprintGenerator fingerprintGenerator) {
        return new CardLinkService(
                codefClient, codefCredentialMapper, issuerMapper, codefEncryptor, fingerprintGenerator);
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

    private static String sendPost(String url, Map<String, String> headers, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("CODEF 요청에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CODEF 요청이 중단되었습니다.", exception);
        }
    }
}
