package com.moca.mocabe.domain.codef.exception;

/**
 * POST /me/cards/sync에서 카드 실적조회가 실패해 동기화 전체를 중단할 때 발생한다.
 *
 * 세 가지 원인을 모두 포함한다: ①카드사가 실적조회 자체를 지원하지 않음(issuers.performance_lookback_months
 * = -1), ②요청한 달이 카드사가 지원하는 조회 가능 범위를 벗어남, ③CODEF 실적조회 호출 자체가 실패함.
 * 원인별로 별도 예외를 두지 않고 메시지로 구분하며, 승인내역 실패(ApprovalSyncFailedException)와는
 * 응답 code로 구분된다.
 */
public class PerformanceSyncFailedException extends RuntimeException {

    public PerformanceSyncFailedException(String message) {
        super(message);
    }

    public PerformanceSyncFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
