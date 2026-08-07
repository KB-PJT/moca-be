package com.moca.mocabe.domain.codef.exception;

/**
 * CODEF Connected ID 발급 시 비밀번호를 여러 번 틀려 카드사 계정이 잠겼을 때 발생한다(CODEF
 * errorList 코드 CF-12802). 일시적인 CODEF 상류 장애가 아니라 계정 자체가 잠긴 상태라 재시도해도
 * 해결되지 않으므로, 재시도 안내(503)가 아니라 잠김 상태를 알리는 423으로 구분해 알려준다.
 */
public class CodefAccountLockedException extends RuntimeException {

    public CodefAccountLockedException() {
        super("비밀번호 오류 횟수를 초과해 카드사 계정이 잠겼습니다. 카드사를 통해 잠금을 해제한 뒤 다시 시도해주세요.");
    }
}
