package com.moca.mocabe.global.exception.home;

/** 홈 화면을 구성할 데이터가 없을 때 발생하는 예외다. */
public class HomeDataNotFoundException extends RuntimeException {

    public HomeDataNotFoundException(String message) {
        super(message);
    }
}
