package com.moca.mocabe.domain.codef.infra;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 중복 연동 확인용 자격정보 fingerprint를 HMAC-SHA256으로 생성한다. */
public class CredentialFingerprintGenerator {

    private static final String DEFAULT_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final String algorithm;

    public CredentialFingerprintGenerator(byte[] key) {
        this(key, DEFAULT_ALGORITHM);
    }

    CredentialFingerprintGenerator(byte[] key, String algorithm) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("fingerprint HMAC 키는 32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(key.clone(), DEFAULT_ALGORITHM);
        this.algorithm = algorithm;
    }

    public String generate(String credentialType, String normalizedCredential) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(key);
            byte[] input = (credentialType + ":" + normalizedCredential).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(input));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("자격정보 fingerprint 생성에 실패했습니다.", exception);
        }
    }
}
