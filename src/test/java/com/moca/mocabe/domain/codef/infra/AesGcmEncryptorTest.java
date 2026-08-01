package com.moca.mocabe.domain.codef.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmEncryptorTest {

    private static final byte[] KEY = new byte[32];

    @Test
    @DisplayName("암호화한 값을 복호화하면 원문으로 복원된다")
    void encryptsAndDecryptsRoundTrip() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor(KEY);

        byte[] ciphertext = encryptor.encrypt("connected-id-1234");

        assertFalse(Arrays.equals("connected-id-1234".getBytes(), ciphertext));
        assertEquals("connected-id-1234", encryptor.decrypt(ciphertext));
    }

    @Test
    @DisplayName("같은 원문도 IV가 매번 달라 다른 암호문이 된다")
    void producesDifferentCiphertextPerCall() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor(KEY);

        assertFalse(Arrays.equals(encryptor.encrypt("same"), encryptor.encrypt("same")));
    }

    @Test
    @DisplayName("null 입력은 null로 처리한다")
    void handlesNull() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor(KEY);

        assertNull(encryptor.encrypt(null));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    @DisplayName("암호화 실패 시 IllegalStateException을 던진다")
    void wrapsEncryptFailure() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor(KEY, "AES/BAD/NoPadding");

        assertThrows(IllegalStateException.class, () -> encryptor.encrypt("value"));
    }

    @Test
    @DisplayName("복호화 실패 시 IllegalStateException을 던진다")
    void wrapsDecryptFailure() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor(KEY, "AES/BAD/NoPadding");

        assertThrows(IllegalStateException.class, () -> encryptor.decrypt(new byte[13]));
    }

    @Test
    @DisplayName("32바이트가 아닌 키는 거부한다")
    void rejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmEncryptor(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new AesGcmEncryptor(null));
    }
}
