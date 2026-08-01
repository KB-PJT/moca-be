package com.moca.mocabe.domain.codef.infra;

/** CODEF HTTP 응답의 상태 코드와 본문이다. */
public record CodefHttpResponse(int statusCode, String body) {
}
