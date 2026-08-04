package com.moca.mocabe.global.exception.home;

/** 홈 API의 조회 조건이 유효하지 않을 때 발생한다. */
public class InvalidHomeQueryException extends RuntimeException {

    public InvalidHomeQueryException(String message) {
        super(message);
    }
}
