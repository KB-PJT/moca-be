package com.moca.mocabe.domain.codef.exception;

/**
 * POST /me/cards/sync에서 CODEF 실적조회 호출 자체가 실패해 동기화 전체를 중단할 때 발생한다.
 * 상류(CODEF) 일시 장애이므로 재시도하면 성공할 수 있다(503).
 *
 * 요청한 달을 애초에 조회할 수 없는 영구 조건(카드사 실적조회 미지원, 조회 가능 범위 초과)은
 * 재시도해도 항상 실패하므로 이 예외가 아니라 {@link PerformanceUnsupportedException}(400)으로
 * 구분한다. 승인내역 실패(ApprovalSyncFailedException)와는 응답 code로 구분된다.
 */
public class PerformanceSyncFailedException extends RuntimeException {

    public PerformanceSyncFailedException(String message) {
        super(message);
    }

    public PerformanceSyncFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
