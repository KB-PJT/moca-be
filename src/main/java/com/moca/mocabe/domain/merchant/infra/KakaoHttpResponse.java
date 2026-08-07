package com.moca.mocabe.domain.merchant.infra;

/** 카카오맵 로컬 API HTTP 응답의 상태 코드와 본문이다. */
public record KakaoHttpResponse(int statusCode, String body) {
}
