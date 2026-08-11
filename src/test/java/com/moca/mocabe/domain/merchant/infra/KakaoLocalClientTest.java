package com.moca.mocabe.domain.merchant.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.global.exception.merchant.KakaoUnavailableException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KakaoLocalClientTest {

    private final KakaoHttpClient httpClient = mock(KakaoHttpClient.class);
    private final KakaoLocalClient kakaoLocalClient = new KakaoLocalClient(httpClient, "test-rest-api-key");

    @Test
    @DisplayName("카테고리 검색 응답의 장소 목록을 KakaoPlace로 변환한다")
    void parsesCategorySearchResponse() {
        String body = "{\"documents\":[{\"place_name\":\"스타벅스 강남점\",\"x\":\"127.028\",\"y\":\"37.498\","
                + "\"distance\":\"120\",\"road_address_name\":\"서울 강남구 테헤란로 1\","
                + "\"address_name\":\"서울 강남구 역삼동 1\"}]}";
        when(httpClient.get(contains("category.json"), any())).thenReturn(new KakaoHttpResponse(200, body));

        List<KakaoPlace> places = kakaoLocalClient.searchByCategory("CE7", 37.5, 127.03, 500);

        assertEquals(1, places.size());
        assertEquals("스타벅스 강남점", places.get(0).placeName());
        assertEquals(37.498, places.get(0).latitude());
        assertEquals(127.028, places.get(0).longitude());
        assertEquals(120, places.get(0).distanceMeters());
        assertEquals("서울 강남구 테헤란로 1", places.get(0).address());
    }

    @Test
    @DisplayName("도로명 주소가 없으면 지번 주소를 쓴다")
    void fallsBackToAddressNameWhenRoadAddressMissing() {
        String body = "{\"documents\":[{\"place_name\":\"이디야\",\"x\":\"127.0\",\"y\":\"37.5\","
                + "\"road_address_name\":\"\",\"address_name\":\"서울 강남구 역삼동 1\"}]}";
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, body));

        List<KakaoPlace> places = kakaoLocalClient.searchByKeyword("이디야", 37.5, 127.0, 500);

        assertEquals("서울 강남구 역삼동 1", places.get(0).address());
    }

    @Test
    @DisplayName("주소 정보가 아예 없으면 null이다")
    void returnsNullAddressWhenMissing() {
        String body = "{\"documents\":[{\"place_name\":\"이디야\",\"x\":\"127.0\",\"y\":\"37.5\"}]}";
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, body));

        List<KakaoPlace> places = kakaoLocalClient.searchByKeyword("이디야", 37.5, 127.0, 500);

        assertEquals(null, places.get(0).address());
    }

    @Test
    @DisplayName("키워드 검색은 Authorization 헤더에 KakaoAK 키를 담아 요청한다")
    void sendsKakaoAkAuthorizationHeader() {
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, "{\"documents\":[]}"));

        kakaoLocalClient.searchByKeyword("스타벅스", 37.5, 127.03, 500);

        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(httpClient).get(contains("keyword.json"), headersCaptor.capture());
        assertEquals("KakaoAK test-rest-api-key", headersCaptor.getValue().get("Authorization"));
    }

    @Test
    @DisplayName("distance가 없으면 distanceMeters는 null이다")
    void allowsMissingDistance() {
        String body = "{\"documents\":[{\"place_name\":\"이디야\",\"x\":\"127.0\",\"y\":\"37.5\"}]}";
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, body));

        List<KakaoPlace> places = kakaoLocalClient.searchByKeyword("이디야", 37.5, 127.0, 500);

        assertEquals(1, places.size());
        assertEquals(null, places.get(0).distanceMeters());
    }

    @Test
    @DisplayName("비2xx 응답이면 KakaoUnavailableException을 던진다")
    void throwsWhenNotSuccessful() {
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(500, "error"));

        assertThrows(KakaoUnavailableException.class,
                () -> kakaoLocalClient.searchByCategory("CE7", 37.5, 127.0, 500));
    }

    @Test
    @DisplayName("응답 본문이 JSON이 아니면 KakaoUnavailableException을 던진다")
    void throwsWhenResponseIsNotJson() {
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, "not-json"));

        assertThrows(KakaoUnavailableException.class,
                () -> kakaoLocalClient.searchByCategory("CE7", 37.5, 127.0, 500));
    }

    @Test
    @DisplayName("검색어는 URL 인코딩되어 전달된다")
    void encodesQueryParameter() {
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, "{\"documents\":[]}"));

        kakaoLocalClient.searchByKeyword("스타벅스 강남", 37.5, 127.0, 500);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(httpClient).get(urlCaptor.capture(), any());
        assertTrue(urlCaptor.getValue().contains("query=%EC%8A%A4%ED%83%80%EB%B2%85%EC%8A%A4"));
    }
}
