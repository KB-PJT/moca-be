package com.moca.mocabe.domain.codef.exception;

/** 보유카드 재조회 시 지정한 기관코드로 연동된 활성 계정이 없을 때 발생한다. */
public class CodefConnectionNotFoundException extends RuntimeException {

    public CodefConnectionNotFoundException(String institutionCode) {
        super("연동된 카드사 계정을 찾을 수 없습니다: " + institutionCode);
    }
}
