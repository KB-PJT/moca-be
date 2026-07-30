package com.moca.mocabe.global.exception.user;

/** 인증 사용자의 계정이 없거나 탈퇴 상태일 때 발생한다. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다.");
    }
}
