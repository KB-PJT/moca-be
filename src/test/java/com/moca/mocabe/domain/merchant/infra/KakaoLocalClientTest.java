package com.moca.mocabe.domain.merchant.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
                + "\"address_name\":\"서울 강남구 역삼동 1\",\"category_group_code\":\"CE7\","
                + "\"category_name\":\"음식점 > 카페 > 커피전문점\"}]}";
        when(httpClient.get(contains("category.json"), any())).thenReturn(new KakaoHttpResponse(200, body));

        List<KakaoPlace> places = kakaoLocalClient.searchByCategory("CE7", 37.5, 127.03, 500);

        assertEquals(1, places.size());
        assertEquals("스타벅스 강남점", places.get(0).placeName());
        assertEquals(37.498, places.get(0).latitude());
        assertEquals(127.028, places.get(0).longitude());
        assertEquals(120, places.get(0).distanceMeters());
        assertEquals("서울 강남구 테헤란로 1", places.get(0).address());
        assertEquals("CE7", places.get(0).categoryGroupCode());
        assertEquals("음식점 > 카페 > 커피전문점", places.get(0).categoryName());
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
    @DisplayName("도로명 주소와 지번 주소가 둘 다 빈 문자열이면 null이다")
    void returnsNullAddressWhenBothFieldsAreEmpty() {
        String body = "{\"documents\":[{\"place_name\":\"이디야\",\"x\":\"127.0\",\"y\":\"37.5\","
                + "\"road_address_name\":\"\",\"address_name\":\"\"}]}";
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

    @Test
    @DisplayName("meta가 없으면 1페이지만 조회하고 끝낸다")
    void stopsAfterFirstPageWhenMetaMissing() {
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, "{\"documents\":[]}"));

        kakaoLocalClient.searchByCategory("CS2", 37.5, 127.0, 500);

        verify(httpClient, times(1)).get(anyString(), any());
    }

    @Test
    @DisplayName("is_end가 false면 다음 페이지를 계속 조회해 최대 3페이지(45건)까지 모은다")
    void paginatesUntilEndOrMaxPages() {
        String page1 = "{\"documents\":[{\"place_name\":\"GS25 1호점\",\"x\":\"127.0\",\"y\":\"37.5\"}],"
                + "\"meta\":{\"is_end\":false}}";
        String page2 = "{\"documents\":[{\"place_name\":\"GS25 2호점\",\"x\":\"127.0\",\"y\":\"37.5\"}],"
                + "\"meta\":{\"is_end\":false}}";
        String page3 = "{\"documents\":[{\"place_name\":\"GS25 3호점\",\"x\":\"127.0\",\"y\":\"37.5\"}],"
                + "\"meta\":{\"is_end\":false}}";
        when(httpClient.get(contains("page=1"), any())).thenReturn(new KakaoHttpResponse(200, page1));
        when(httpClient.get(contains("page=2"), any())).thenReturn(new KakaoHttpResponse(200, page2));
        when(httpClient.get(contains("page=3"), any())).thenReturn(new KakaoHttpResponse(200, page3));

        List<KakaoPlace> places = kakaoLocalClient.searchByCategory("CS2", 37.5, 127.0, 3000);

        assertEquals(3, places.size());
        assertEquals("GS25 1호점", places.get(0).placeName());
        assertEquals("GS25 2호점", places.get(1).placeName());
        assertEquals("GS25 3호점", places.get(2).placeName());
        verify(httpClient, times(3)).get(anyString(), any());
    }

    @Test
    @DisplayName("is_end가 true가 되면 그 페이지에서 조회를 멈춘다")
    void stopsWhenIsEndTrue() {
        String page1 = "{\"documents\":[{\"place_name\":\"GS25 1호점\",\"x\":\"127.0\",\"y\":\"37.5\"}],"
                + "\"meta\":{\"is_end\":true}}";
        when(httpClient.get(anyString(), any())).thenReturn(new KakaoHttpResponse(200, page1));

        List<KakaoPlace> places = kakaoLocalClient.searchByCategory("CS2", 37.5, 127.0, 500);

        assertEquals(1, places.size());
        verify(httpClient, times(1)).get(anyString(), any());
    }
}
