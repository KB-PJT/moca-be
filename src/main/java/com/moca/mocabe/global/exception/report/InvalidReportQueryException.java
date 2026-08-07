package com.moca.mocabe.global.exception.report;

/** 혜택·실적 리포트 조회 파라미터가 계약을 만족하지 않을 때 발생한다. */
public class InvalidReportQueryException extends RuntimeException {

    public InvalidReportQueryException(String message) {
        super(message);
    }
}
