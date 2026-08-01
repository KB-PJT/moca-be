package com.moca.mocabe.domain.codef.infra;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.moca.mocabe.domain.codef.model.CodefConnectionCommand;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 실제 CODEF 데모 API를 호출해 Connected ID가 발급되는지 확인하는 수동 통합 테스트다.
 *
 * DB·스프링 컨텍스트 없이 프로덕션 {@link CodefClient}만 직접 호출한다. `check`에서 제외되는
 * integration 태그이므로 아래처럼 수동 실행한다(프로젝트 루트 .env 또는 환경변수에서 설정을 읽는다)
 *
 * ./gradlew integrationTest --tests "*CodefConnectedIdIntegrationTest"
 *
 * .env 예시 키: MOCA_CODEF_CLIENT_ID, MOCA_CODEF_CLIENT_SECRET, MOCA_CODEF_PUBLIC_KEY,
 * CODEF_ORGANIZATION, CODEF_ID, CODEF_PASSWORD, (선택) CODEF_CARD_NO, CODEF_CARD_PASSWORD,
 * CODEF_BIRTHDATE, MOCA_CODEF_BASE_URL(기본 https://development.codef.io),
 * MOCA_CODEF_TOKEN_URL(기본 https://oauth.codef.io/oauth/token).
 */
@Tag("integration")
class CodefConnectedIdIntegrationTest {

    private static final String DEFAULT_BASE_URL = "https://development.codef.io";
    private static final String DEFAULT_TOKEN_URL = "https://oauth.codef.io/oauth/token";

    @Test
    @DisplayName("CODEF 데모에서 Connected ID가 발급된다")
    void createsConnectedId() throws IOException {
        Map<String, String> config = loadConfig();
        assumeTrue(hasRequiredKeys(config),
                "CODEF 설정(.env 또는 환경변수)이 없어 통합 테스트를 건너뜁니다.");

        CodefClient codefClient = new CodefClient(
                CodefConnectedIdIntegrationTest::sendPost,
                config.get("MOCA_CODEF_CLIENT_ID"), config.get("MOCA_CODEF_CLIENT_SECRET"),
                config.get("MOCA_CODEF_PUBLIC_KEY"),
                config.getOrDefault("MOCA_CODEF_BASE_URL", DEFAULT_BASE_URL),
                config.getOrDefault("MOCA_CODEF_TOKEN_URL", DEFAULT_TOKEN_URL));

        CodefConnectionCommand command = new CodefConnectionCommand(
                config.get("CODEF_ORGANIZATION"),
                config.get("CODEF_ID"), config.get("CODEF_PASSWORD"),
                config.get("CODEF_CARD_NO"), config.get("CODEF_CARD_PASSWORD"), config.get("CODEF_BIRTHDATE"));

        String connectedId = codefClient.createConnectedId(command);

        assertNotNull(connectedId);
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
            throw new IllegalStateException("CODEF 요청 실패", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CODEF 요청 중단", exception);
        }
    }

    private boolean hasRequiredKeys(Map<String, String> config) {
        for (String key : new String[] {"MOCA_CODEF_CLIENT_ID", "MOCA_CODEF_CLIENT_SECRET",
                "MOCA_CODEF_PUBLIC_KEY",
                "CODEF_ORGANIZATION", "CODEF_ID", "CODEF_PASSWORD"}) {
            if (config.get(key) == null || config.get(key).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> loadConfig() throws IOException {
        Map<String, String> config = new HashMap<>();
        Path envFile = Path.of(System.getenv().getOrDefault("CODEF_ENV_FILE", ".env"));
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                config.put(trimmed.substring(0, separator).trim(),
                        stripQuotes(trimmed.substring(separator + 1).trim()));
            }
        }
        config.putAll(System.getenv());
        return config;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
