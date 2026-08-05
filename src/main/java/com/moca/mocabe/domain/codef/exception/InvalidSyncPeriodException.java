package com.moca.mocabe.domain.codef.exception;

/** 승인내역 동기화 조회 기간이 올바르지 않을 때 발생한다(시작일이 종료일보다 늦은 경우 등). */
public class InvalidSyncPeriodException extends RuntimeException {

    public InvalidSyncPeriodException(String message) {
        super(message);
    }
}
