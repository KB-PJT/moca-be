package com.moca.mocabe.global.exception.merchant;

/**
 * 카카오맵 로컬 API 호출이 타임아웃·연결 실패·비2xx 응답으로 일시적으로 실패했을 때 발생한다.
 * 서버 내부 오류(500)가 아니라 상류 서비스의 일시 장애이므로 재시도를 안내하는 503으로 내려보낸다.
 */
public class KakaoUnavailableException extends RuntimeException {

    public KakaoUnavailableException(String message) {
        super(message);
    }

    public KakaoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
