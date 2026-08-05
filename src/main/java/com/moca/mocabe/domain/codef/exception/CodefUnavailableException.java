package com.moca.mocabe.domain.codef.exception;

/**
 * CODEF 외부 연동이 타임아웃·연결 실패 등으로 일시적으로 응답하지 못할 때 발생한다.
 *
 * 서버 내부 오류(500)가 아니라 상류 서비스의 일시 장애이므로, 재시도를 안내하는 503으로 내려보낸다.
 */
public class CodefUnavailableException extends RuntimeException {

    public CodefUnavailableException(String message) {
        super(message);
    }

    public CodefUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
