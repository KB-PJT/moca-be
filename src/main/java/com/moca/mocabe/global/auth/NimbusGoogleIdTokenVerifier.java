package com.moca.mocabe.global.auth;

import com.moca.mocabe.global.exception.auth.InvalidGoogleIdTokenException;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Google Discovery JWKS를 사용해 ID Token의 서명과 iss, aud, exp를 검증한다. */
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final NimbusJwtDecoder jwtDecoder;

    public NimbusGoogleIdTokenVerifier(String clientId) {
        this(NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build(), requireClientId(clientId));
    }

    NimbusGoogleIdTokenVerifier(NimbusJwtDecoder jwtDecoder, String clientId) {
        this.jwtDecoder = jwtDecoder;
        this.jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER),
                audienceValidator(clientId)));
    }

    @Override
    public GoogleIdTokenClaims verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            String subject = jwt.getSubject();
            if (subject == null || subject.trim().isEmpty()) {
                throw new InvalidGoogleIdTokenException();
            }
            return new GoogleIdTokenClaims(subject, jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidGoogleIdTokenException) {
                throw exception;
            }
            throw new InvalidGoogleIdTokenException();
        }
    }

    OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
        return token -> {
            List<String> audiences = token.getAudience();
            if (!audiences.contains(clientId)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            if (audiences.size() > 1 && !clientId.equals(token.getClaimAsString("azp"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static String requireClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("MOCA_GOOGLE_CLIENT_ID는 필수입니다.");
        }
        return clientId.trim();
    }
}
