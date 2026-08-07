package com.moca.mocabe.domain.merchant.infra;

import java.util.Map;

/**
 * 카카오맵 로컬 API 호출의 HTTP 전송만 담당하는 seam이다.
 *
 * 실제 네트워크 구현은 설정(AppConfig)에서 주입하고, KakaoLocalClient의 로직은 이 인터페이스를
 * 목으로 대체해 단위 테스트한다.
 */
@FunctionalInterface
public interface KakaoHttpClient {

    KakaoHttpResponse get(String url, Map<String, String> headers);
}
