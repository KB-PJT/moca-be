package com.moca.mocabe.domain.codef.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialHasherTest {

    private static final byte[] KEY = new byte[32];

    @Test
    @DisplayName("같은 자격정보는 같은 해시를 생성한다")
    void generatesDeterministicHash() {
        CredentialHasher hasher = new CredentialHasher(KEY);

        String first = hasher.generate("CARD_NO", "1234567890123456");
        String second = hasher.generate("CARD_NO", "1234567890123456");

        assertEquals(64, first.length());
        assertEquals(first, second);
        assertNotEquals(first, hasher.generate("ACCOUNT_ID", "1234567890123456"));
    }

    @Test
    @DisplayName("32바이트가 아닌 HMAC 키는 거부한다")
    void rejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialHasher(new byte[16]));
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialHasher(null));
    }

    @Test
    @DisplayName("지원하지 않는 HMAC 알고리즘은 해시 생성 오류로 변환한다")
    void wrapsHashFailure() {
        CredentialHasher hasher = new CredentialHasher(KEY, "HmacMissing");

        assertThrows(IllegalStateException.class,
                () -> hasher.generate("ACCOUNT_ID", "tester"));
    }
}
