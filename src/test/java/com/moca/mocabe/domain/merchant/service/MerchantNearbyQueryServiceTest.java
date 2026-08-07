package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.dto.NearbyMerchantResponse;
import com.moca.mocabe.domain.merchant.infra.KakaoLocalClient;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.domain.merchant.model.MerchantListRow;
import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.MerchantCategoryNotFoundException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantNearbyQueryServiceTest {

    private static final String CATEGORY_ID = "cat-cafe";

    private final MerchantCategoryMapper merchantCategoryMapper = mock(MerchantCategoryMapper.class);
    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final MerchantLookup merchantLookup = mock(MerchantLookup.class);
    private final KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
    private final MerchantNearbyQueryService service = new MerchantNearbyQueryService(
            merchantCategoryMapper, merchantMapper, merchantLookup, kakaoLocalClient);

    @Test
    @DisplayName("그룹코드 매핑이 있으면 카테고리 검색을 호출하고 우리 DB 가맹점만 남긴다")
    void usesCategorySearchWhenGroupCodeMapped() {
        when(merchantCategoryMapper.existsMapVisibleCategory(CATEGORY_ID)).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID)).thenReturn(
                List.of(new MerchantListRow("m-starbucks", "스타벅스")));
        when(merchantCategoryMapper.findEnabledKakaoGroupCodes(CATEGORY_ID)).thenReturn(List.of("CE7"));
        when(kakaoLocalClient.searchByCategory("CE7", 37.5, 127.0, 500)).thenReturn(List.of(
                new KakaoPlace("스타벅스 강남점", 37.501, 127.001, 120),
                new KakaoPlace("이름모를카페", 37.502, 127.002, 200)));
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(
                List.of(new MerchantNameCandidate("m-starbucks", "스타벅스")), List.of()));

        List<NearbyMerchantResponse> results = service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 500);

        assertEquals(1, results.size());
        assertEquals("m-starbucks", results.get(0).merchantId());
        assertEquals("스타벅스", results.get(0).name());
        assertEquals(120, results.get(0).distanceMeters());
        verify(kakaoLocalClient, never()).searchByKeyword(anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("그룹코드 매핑이 없으면 카테고리 가맹점명마다 키워드 검색을 호출한다")
    void usesKeywordSearchWhenNoGroupCodeMapped() {
        when(merchantCategoryMapper.existsMapVisibleCategory(CATEGORY_ID)).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID)).thenReturn(List.of(
                new MerchantListRow("m-olive", "올리브영")));
        when(merchantCategoryMapper.findEnabledKakaoGroupCodes(CATEGORY_ID)).thenReturn(List.of());
        when(kakaoLocalClient.searchByKeyword("올리브영", 37.5, 127.0, 500)).thenReturn(List.of(
                new KakaoPlace("올리브영 홍대점", 37.55, 127.05, 300)));
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(
                List.of(new MerchantNameCandidate("m-olive", "올리브영")), List.of()));

        List<NearbyMerchantResponse> results = service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 500);

        assertEquals(1, results.size());
        assertEquals("m-olive", results.get(0).merchantId());
        verify(kakaoLocalClient, never()).searchByCategory(anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("동일 가맹점이 여러 번 검색되면 가장 가까운 결과만 남긴다")
    void keepsClosestDuplicate() {
        when(merchantCategoryMapper.existsMapVisibleCategory(CATEGORY_ID)).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID)).thenReturn(List.of(
                new MerchantListRow("m-starbucks", "스타벅스")));
        when(merchantCategoryMapper.findEnabledKakaoGroupCodes(CATEGORY_ID)).thenReturn(List.of("CE7"));
        when(kakaoLocalClient.searchByCategory("CE7", 37.5, 127.0, 500)).thenReturn(List.of(
                new KakaoPlace("스타벅스 강남점", 37.501, 127.001, 300),
                new KakaoPlace("스타벅스 역삼점", 37.502, 127.002, 100)));
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(
                List.of(new MerchantNameCandidate("m-starbucks", "스타벅스")), List.of()));

        List<NearbyMerchantResponse> results = service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 500);

        assertEquals(1, results.size());
        assertEquals(100, results.get(0).distanceMeters());
    }

    @Test
    @DisplayName("distance가 없는 결과보다 distance가 있는 결과를 더 가까운 것으로 취급한다")
    void prefersPlaceWithKnownDistanceOverUnknown() {
        when(merchantCategoryMapper.existsMapVisibleCategory(CATEGORY_ID)).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID)).thenReturn(List.of(
                new MerchantListRow("m-starbucks", "스타벅스")));
        when(merchantCategoryMapper.findEnabledKakaoGroupCodes(CATEGORY_ID)).thenReturn(List.of("CE7"));
        when(kakaoLocalClient.searchByCategory("CE7", 37.5, 127.0, 500)).thenReturn(List.of(
                new KakaoPlace("스타벅스 강남점", 37.501, 127.001, null),
                new KakaoPlace("스타벅스 역삼점", 37.502, 127.002, 150)));
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(
                List.of(new MerchantNameCandidate("m-starbucks", "스타벅스")), List.of()));

        List<NearbyMerchantResponse> results = service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 500);

        assertEquals(1, results.size());
        assertEquals(150, results.get(0).distanceMeters());
    }

    @Test
    @DisplayName("두 결과 모두 distance가 없으면 먼저 찾은 결과를 유지한다")
    void keepsFirstWhenBothDistancesUnknown() {
        when(merchantCategoryMapper.existsMapVisibleCategory(CATEGORY_ID)).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID)).thenReturn(List.of(
                new MerchantListRow("m-starbucks", "스타벅스")));
        when(merchantCategoryMapper.findEnabledKakaoGroupCodes(CATEGORY_ID)).thenReturn(List.of("CE7"));
        when(kakaoLocalClient.searchByCategory("CE7", 37.5, 127.0, 500)).thenReturn(List.of(
                new KakaoPlace("스타벅스 강남점", 37.501, 127.001, null),
                new KakaoPlace("스타벅스 역삼점", 37.502, 127.002, null)));
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(
                List.of(new MerchantNameCandidate("m-starbucks", "스타벅스")), List.of()));

        List<NearbyMerchantResponse> results = service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 500);

        assertEquals(1, results.size());
        assertEquals(37.501, results.get(0).latitude());
    }

    @Test
    @DisplayName("카테고리에 활성 가맹점이 없으면 카카오를 호출하지 않고 빈 목록을 반환한다")
    void returnsEmptyWhenNoMerchantsInCategory() {
        when(merchantCategoryMapper.existsMapVisibleCategory(CATEGORY_ID)).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID)).thenReturn(List.of());

        assertTrue(service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 500).isEmpty());
        verify(merchantCategoryMapper, never()).findEnabledKakaoGroupCodes(anyString());
    }

    @Test
    @DisplayName("categoryId가 비어 있으면 예외를 던진다")
    void rejectsBlankCategoryId() {
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(" ", 37.5, 127.0, 500));
    }

    @Test
    @DisplayName("latitude, longitude가 없으면 예외를 던진다")
    void rejectsMissingCoordinates() {
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, null, 127.0, 500));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, 37.5, null, 500));
    }

    @Test
    @DisplayName("latitude, longitude가 범위를 벗어나면 예외를 던진다")
    void rejectsCoordinatesOutOfRange() {
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, 90.1, 127.0, 500));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, -90.1, 127.0, 500));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, 37.5, 180.1, 500));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, 37.5, -180.1, 500));
        verifyNoInteractions(merchantCategoryMapper, merchantMapper, kakaoLocalClient);
    }

    @Test
    @DisplayName("radiusMeters가 범위를 벗어나면 예외를 던진다")
    void rejectsRadiusOutOfRange() {
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 99));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, 3001));
        verifyNoInteractions(merchantCategoryMapper);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리면 가맹점을 조회하지 않고 예외를 던진다")
    void rejectsUnknownCategory() {
        when(merchantCategoryMapper.existsMapVisibleCategory("cat-unknown")).thenReturn(false);

        assertThrows(MerchantCategoryNotFoundException.class,
                () -> service.getNearbyMerchants("cat-unknown", 37.5, 127.0, 500));

        verify(merchantMapper, never()).findActiveMerchantsByCategoryId(anyString());
    }

    @Test
    @DisplayName("radiusMeters를 생략하면 기본값 150으로 검색한다")
    void usesDefaultRadiusWhenOmitted() {
        when(merchantCategoryMapper.existsMapVisibleCategory(CATEGORY_ID)).thenReturn(true);
        when(merchantMapper.findActiveMerchantsByCategoryId(CATEGORY_ID)).thenReturn(List.of(
                new MerchantListRow("m-starbucks", "스타벅스")));
        when(merchantCategoryMapper.findEnabledKakaoGroupCodes(CATEGORY_ID)).thenReturn(List.of("CE7"));
        when(kakaoLocalClient.searchByCategory("CE7", 37.5, 127.0, 150)).thenReturn(List.of());
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(List.of(), List.of()));

        service.getNearbyMerchants(CATEGORY_ID, 37.5, 127.0, null);

        verify(kakaoLocalClient).searchByCategory("CE7", 37.5, 127.0, 150);
    }

    private MerchantCandidateSnapshot snapshot(List<MerchantNameCandidate> nameCandidates,
                                               List<MerchantNameCandidate> aliasCandidates) {
        return new MerchantCandidateSnapshot(nameCandidates, aliasCandidates, new MerchantNameNormalizer());
    }
}
