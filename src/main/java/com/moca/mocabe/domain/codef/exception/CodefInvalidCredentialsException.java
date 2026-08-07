package com.moca.mocabe.domain.codef.exception;

/**
 * CODEF Connected ID 발급 시 카드사 로그인 아이디·비밀번호가 틀렸을 때 발생한다(CODEF errorList
 * 코드 CF-12803). CODEF 상류 장애가 아니라 사용자 입력 오류이므로 재시도 안내(503)가 아니라
 * 400으로 구분해 알려준다.
 */
public class CodefInvalidCredentialsException extends RuntimeException {

    public CodefInvalidCredentialsException() {
        super("아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
