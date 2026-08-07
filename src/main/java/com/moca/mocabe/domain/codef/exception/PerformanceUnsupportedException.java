package com.moca.mocabe.domain.codef.exception;

/**
 * POST /me/cards/sync에서 요청한 실적 조회 대상 월을 애초에 받을 수 없는, 재시도해도 항상 실패하는
 * 영구 조건일 때 발생한다: ①카드사가 실적조회 자체를 지원하지 않음(issuers.performance_lookback_months
 * = -1), ②요청한 달이 카드사가 지원하는 조회 가능 범위를 벗어남. 같은 요청을 다시 보내도 결과가 바뀌지
 * 않으므로 재시도 가능을 뜻하는 503이 아니라 400으로 응답한다. CODEF 호출 자체가 실패하는 일시적 상황은
 * {@link PerformanceSyncFailedException}으로 구분한다.
 */
public class PerformanceUnsupportedException extends RuntimeException {

    public PerformanceUnsupportedException(String message) {
        super(message);
    }
}
