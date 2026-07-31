package com.moca.mocabe.global.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moca.mocabe.global.exception.auth.InvalidGoogleIdTokenException;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class NimbusGoogleIdTokenVerifierTest {

    @Test
    @DisplayName("Google ID Token의 sub, email, name을 내부 인증 정보로 변환한다")
    void verifiesGoogleIdTokenClaims() {
        NimbusJwtDecoder decoder = mock(NimbusJwtDecoder.class);
        Jwt jwt = mock(Jwt.class);
        when(decoder.decode("id-token")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("google-subject");
        when(jwt.getClaimAsString("email")).thenReturn("moca@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("모카");

        GoogleIdTokenClaims claims = new NimbusGoogleIdTokenVerifier(decoder, "client-id").verify("id-token");

        assertEquals("google-subject", claims.getSubject());
        assertEquals("moca@example.com", claims.getEmail());
        assertEquals("모카", claims.getName());
    }

    @Test
    @DisplayName("서명이 잘못되었거나 sub가 없는 Google ID Token은 거절한다")
    void rejectsInvalidGoogleIdToken() {
        NimbusJwtDecoder decoder = mock(NimbusJwtDecoder.class);
        when(decoder.decode("invalid"))
                .thenThrow(new org.springframework.security.oauth2.jwt.JwtException("invalid token"));
        NimbusGoogleIdTokenVerifier verifier = new NimbusGoogleIdTokenVerifier(decoder, "client-id");

        assertThrows(InvalidGoogleIdTokenException.class, () -> verifier.verify("invalid"));
        Jwt jwt = mock(Jwt.class);
        when(decoder.decode("blank-sub")).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(" ");
        assertThrows(InvalidGoogleIdTokenException.class, () -> verifier.verify("blank-sub"));
    }

    @Test
    @DisplayName("Google audience validator는 등록된 클라이언트 ID만 허용한다")
    void validatesAudience() {
        NimbusGoogleIdTokenVerifier verifier = new NimbusGoogleIdTokenVerifier("client-id");
        Jwt accepted = jwt(Arrays.asList("client-id"), null);
        Jwt rejected = jwt(Arrays.asList("other-client"), null);

        OAuth2TokenValidatorResult acceptedResult = verifier.audienceValidator("client-id").validate(accepted);
        OAuth2TokenValidatorResult rejectedResult = verifier.audienceValidator("client-id").validate(rejected);

        org.junit.jupiter.api.Assertions.assertFalse(acceptedResult.hasErrors());
        org.junit.jupiter.api.Assertions.assertTrue(rejectedResult.hasErrors());
    }

    @Test
    @DisplayName("다중 audience Google ID Token은 azp가 MOCA Client ID와 일치할 때만 허용한다")
    void validatesAuthorizedPartyForMultipleAudiences() {
        NimbusGoogleIdTokenVerifier verifier = new NimbusGoogleIdTokenVerifier("client-id");
        OAuth2TokenValidatorResult accepted = verifier.audienceValidator("client-id")
                .validate(jwt(Arrays.asList("client-id", "another-client"), "client-id"));
        OAuth2TokenValidatorResult rejected = verifier.audienceValidator("client-id")
                .validate(jwt(Arrays.asList("client-id", "another-client"), "another-client"));

        org.junit.jupiter.api.Assertions.assertFalse(accepted.hasErrors());
        org.junit.jupiter.api.Assertions.assertTrue(rejected.hasErrors());
    }

    @Test
    @DisplayName("Google issuer validator는 공식 및 레거시 issuer만 허용한다")
    void validatesIssuer() {
        NimbusGoogleIdTokenVerifier verifier = new NimbusGoogleIdTokenVerifier("client-id");

        OAuth2TokenValidatorResult official = verifier.issuerValidator().validate(jwtWithIssuer("https://accounts.google.com"));
        OAuth2TokenValidatorResult legacy = verifier.issuerValidator().validate(jwtWithIssuer("accounts.google.com"));
        OAuth2TokenValidatorResult rejected = verifier.issuerValidator().validate(jwtWithIssuer("https://example.com"));

        org.junit.jupiter.api.Assertions.assertFalse(official.hasErrors());
        org.junit.jupiter.api.Assertions.assertFalse(legacy.hasErrors());
        org.junit.jupiter.api.Assertions.assertTrue(rejected.hasErrors());
    }

    @Test
    @DisplayName("Google Client ID가 비어 있으면 ID Token 검증기를 만들 수 없다")
    void rejectsBlankClientId() {
        assertThrows(IllegalArgumentException.class, () -> new NimbusGoogleIdTokenVerifier(" "));
    }

    private Jwt jwt(java.util.List<String> audiences, String authorizedParty) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "subject")
                .audience(audiences)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (authorizedParty != null) {
            builder.claim("azp", authorizedParty);
        }
        return builder.build();
    }

    private Jwt jwtWithIssuer(String issuer) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("iss", issuer)
                .subject("subject")
                .audience(java.util.Collections.singletonList("client-id"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
