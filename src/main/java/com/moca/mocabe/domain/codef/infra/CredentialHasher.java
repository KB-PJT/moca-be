package com.moca.mocabe.domain.codef.infra;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 자격정보/카드 식별값을 HMAC-SHA256으로 해싱한다. 중복 연동 확인 해시와 CODEF 카드 키 해시에 사용한다. */
public class CredentialHasher {

    private static final String DEFAULT_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final String algorithm;

    public CredentialHasher(byte[] key) {
        this(key, DEFAULT_ALGORITHM);
    }

    CredentialHasher(byte[] key, String algorithm) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("해시 HMAC 키는 32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(key.clone(), DEFAULT_ALGORITHM);
        this.algorithm = algorithm;
    }

    public String generate(String hashType, String normalizedValue) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(key);
            byte[] input = (hashType + ":" + normalizedValue).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(input));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("자격정보 해시 생성에 실패했습니다.", exception);
        }
    }
}
