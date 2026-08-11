package com.moca.mocabe.domain.merchant.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.global.exception.merchant.KakaoUnavailableException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** 카카오맵 로컬 API(카테고리 검색/키워드 검색)를 호출해 내부 모델({@link KakaoPlace})로 변환한다. */
public class KakaoLocalClient {

    private static final Logger LOGGER = Logger.getLogger(KakaoLocalClient.class.getName());

    private static final String CATEGORY_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final String KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final int PAGE_SIZE = 15;

    private final KakaoHttpClient httpClient;
    private final String restApiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KakaoLocalClient(KakaoHttpClient httpClient, String restApiKey) {
        this.httpClient = httpClient;
        this.restApiKey = restApiKey;
    }

    /** 카카오 카테고리 그룹코드(MT1/CS2/CE7 등) 기준 반경 검색이다. */
    public List<KakaoPlace> searchByCategory(String categoryGroupCode, double latitude, double longitude,
                                             int radiusMeters) {
        String url = String.format(Locale.ROOT,
                "%s?category_group_code=%s&x=%s&y=%s&radius=%d&size=%d&sort=distance",
                CATEGORY_SEARCH_URL, encode(categoryGroupCode), encode(String.valueOf(longitude)),
                encode(String.valueOf(latitude)), radiusMeters, PAGE_SIZE);
        return search(url);
    }

    /** 가맹점명을 키워드로 한 반경 검색이다. */
    public List<KakaoPlace> searchByKeyword(String query, double latitude, double longitude, int radiusMeters) {
        String url = String.format(Locale.ROOT,
                "%s?query=%s&x=%s&y=%s&radius=%d&size=%d&sort=distance",
                KEYWORD_SEARCH_URL, encode(query), encode(String.valueOf(longitude)),
                encode(String.valueOf(latitude)), radiusMeters, PAGE_SIZE);
        return search(url);
    }

    private List<KakaoPlace> search(String url) {
        KakaoHttpResponse response = httpClient.get(url, Map.of("Authorization", "KakaoAK " + restApiKey));
        if (response.statusCode() != 200) {
            throw new KakaoUnavailableException("카카오맵 응답 오류(HTTP " + response.statusCode() + ")");
        }
        List<KakaoPlace> places = parsePlaces(response.body());
        LOGGER.fine(() -> "카카오맵 검색 " + places.size() + "건: url=" + url);
        return places;
    }

    private List<KakaoPlace> parsePlaces(String body) {
        JsonNode root = readTree(body);
        List<KakaoPlace> places = new ArrayList<>();
        for (JsonNode document : root.path("documents")) {
            places.add(new KakaoPlace(
                    document.path("place_name").asText(),
                    Double.parseDouble(document.path("y").asText()),
                    Double.parseDouble(document.path("x").asText()),
                    parseDistance(document.path("distance")),
                    parseAddress(document)));
        }
        return places;
    }

    /** 도로명 주소가 있으면 그 값, 없으면 지번 주소를 쓴다(카카오 응답은 도로명 주소가 없으면 빈 문자열을 준다). */
    private String parseAddress(JsonNode document) {
        String roadAddress = normalizeAddress(document.path("road_address_name").asText(null));
        if (roadAddress != null) {
            return roadAddress;
        }
        return normalizeAddress(document.path("address_name").asText(null));
    }

    /** 없거나 공백뿐인 주소는 null로 통일한다(필드가 있어도 빈 문자열일 수 있어 asText 기본값만으로는 못 걸러진다). */
    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String trimmed = address.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseDistance(JsonNode distanceNode) {
        String text = distanceNode.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new KakaoUnavailableException("카카오맵 응답을 해석하지 못했습니다.", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
