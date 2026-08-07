package com.moca.mocabe.global.auth;

/** Google OAuth 응답의 상태와 본문이다. */
public class GoogleOAuthHttpResponse {

    private final int statusCode;
    private final String body;

    public GoogleOAuthHttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
