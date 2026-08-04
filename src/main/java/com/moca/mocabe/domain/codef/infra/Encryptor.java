package com.moca.mocabe.domain.codef.infra;

/** 자격정보를 양방향 암호화/복호화한다(CODEF에 재전송하려면 복호화가 가능해야 하므로 해시가 아니다). */
public interface Encryptor {

    byte[] encrypt(String plaintext);

    String decrypt(byte[] ciphertext);
}
