package com.moca.mocabe.domain.codef.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialFingerprintGeneratorTest {

    private static final byte[] KEY = new byte[32];

    @Test
    @DisplayName("같은 자격정보는 같은 fingerprint를 생성한다")
    void generatesDeterministicFingerprint() {
        CredentialFingerprintGenerator generator = new CredentialFingerprintGenerator(KEY);

        String first = generator.generate("CARD_NO", "1234567890123456");
        String second = generator.generate("CARD_NO", "1234567890123456");

        assertEquals(64, first.length());
        assertEquals(first, second);
        assertNotEquals(first, generator.generate("ACCOUNT_ID", "1234567890123456"));
    }

    @Test
    @DisplayName("32바이트가 아닌 HMAC 키는 거부한다")
    void rejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialFingerprintGenerator(new byte[16]));
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialFingerprintGenerator(null));
    }

    @Test
    @DisplayName("지원하지 않는 HMAC 알고리즘은 fingerprint 생성 오류로 변환한다")
    void wrapsFingerprintFailure() {
        CredentialFingerprintGenerator generator = new CredentialFingerprintGenerator(KEY, "HmacMissing");

        assertThrows(IllegalStateException.class,
                () -> generator.generate("ACCOUNT_ID", "tester"));
    }
}
