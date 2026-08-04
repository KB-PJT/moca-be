package com.moca.mocabe.domain.codef.infra;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM으로 자격정보를 양방향 암호화한다.
 *
 * 매 암호화마다 12바이트 IV를 생성해 암호문 앞에 붙여 저장하고, 복호화 시 다시 분리한다.
 * 키는 DB가 아닌 외부(환경변수 등)에서 주입한다.
 */
public class AesGcmEncryptor implements Encryptor {

    private static final String DEFAULT_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final String transformation;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptor(byte[] key) {
        this(key, DEFAULT_TRANSFORMATION);
    }

    AesGcmEncryptor(byte[] key, String transformation) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256 키는 32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
        this.transformation = transformation;
    }

    @Override
    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv); // 매 암호화마다 새 IV 생성
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // IV를 암호문 앞에 붙여 하나로 저장한다
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("자격정보 암호화에 실패했습니다.", exception);
        }
    }

    @Override
    public String decrypt(byte[] ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            // 앞 12바이트는 IV, 나머지가 실제 암호문
            byte[] iv = Arrays.copyOfRange(ciphertext, 0, IV_LENGTH);
            byte[] body = Arrays.copyOfRange(ciphertext, IV_LENGTH, ciphertext.length);
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("자격정보 복호화에 실패했습니다.", exception);
        }
    }
}
