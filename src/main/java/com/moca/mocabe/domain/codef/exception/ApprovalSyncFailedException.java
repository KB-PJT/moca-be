package com.moca.mocabe.domain.codef.exception;

/**
 * POST /me/cards/sync에서 CODEF 승인내역 조회가 실패해 동기화 전체를 중단할 때 발생한다.
 *
 * 실적조회 실패(PerformanceSyncFailedException)와 원인을 구분해 응답 code로 내려보내기 위한 승인내역 전용 예외다.
 */
public class ApprovalSyncFailedException extends RuntimeException {

    public ApprovalSyncFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
