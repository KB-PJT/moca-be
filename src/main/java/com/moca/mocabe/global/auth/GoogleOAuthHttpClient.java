package com.moca.mocabe.global.auth;

import java.util.Map;

/** Google OAuth HTTP 전송 seam이다. */
public interface GoogleOAuthHttpClient {

    GoogleOAuthHttpResponse postForm(String url, Map<String, String> form);

    GoogleOAuthHttpResponse get(String url);
}
