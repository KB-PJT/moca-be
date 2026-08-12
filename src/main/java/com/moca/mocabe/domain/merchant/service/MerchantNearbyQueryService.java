package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.dto.NearbyMerchantResponse;
import com.moca.mocabe.domain.merchant.infra.KakaoLocalClient;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantMapper;
import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.domain.merchant.model.MerchantListRow;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.MerchantCategoryNotFoundException;
import com.moca.mocabe.global.exception.merchant.MerchantNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리별 근처 가맹점 조회 유스케이스를 담당한다.
 *
 * 카테고리가 카카오 카테고리 그룹코드에 매핑돼 있으면(1안) 그룹코드 반경 검색을 그룹코드 개수만큼
 * 호출하고, 매핑이 없으면(2안) 그 카테고리의 활성 가맹점명 각각으로 키워드 반경 검색을 호출해 합친다.
 * 카카오 검색 결과는 접두사 매칭({@link MerchantLookup})으로 우리 DB 가맹점과 대조해, 요청한 카테고리에
 * 속한 가맹점만 남긴다. merchantId를 함께 주면 그 카테고리 안에서 해당 가맹점(브랜드)만 조회한다.
 */
public class MerchantNearbyQueryService {

    private static final Logger LOGGER = Logger.getLogger(MerchantNearbyQueryService.class.getName());

    private static final int DEFAULT_RADIUS_METERS = 150;
    private static final int MIN_RADIUS_METERS = 100;
    private static final int MAX_RADIUS_METERS = 3000;
    private static final double MIN_LATITUDE = -90;
    private static final double MAX_LATITUDE = 90;
    private static final double MIN_LONGITUDE = -180;
    private static final double MAX_LONGITUDE = 180;

    private final MerchantCategoryMapper merchantCategoryMapper;
    private final MerchantMapper merchantMapper;
    private final MerchantLookup merchantLookup;
    private final KakaoLocalClient kakaoLocalClient;

    public MerchantNearbyQueryService(MerchantCategoryMapper merchantCategoryMapper, MerchantMapper merchantMapper,
                                      MerchantLookup merchantLookup, KakaoLocalClient kakaoLocalClient) {
        this.merchantCategoryMapper = merchantCategoryMapper;
        this.merchantMapper = merchantMapper;
        this.merchantLookup = merchantLookup;
        this.kakaoLocalClient = kakaoLocalClient;
    }

    @Transactional(readOnly = true)
    public List<NearbyMerchantResponse> getNearbyMerchants(String categoryId, Double latitude, Double longitude,
                                                            Integer requestedRadiusMeters, String merchantId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new InvalidMerchantQueryException("categoryId는 필수입니다.");
        }
        if (latitude == null || longitude == null) {
            throw new InvalidMerchantQueryException("latitude, longitude는 필수입니다.");
        }
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new InvalidMerchantQueryException(
                    "latitude는 " + MIN_LATITUDE + "~" + MAX_LATITUDE + " 사이여야 합니다.");
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new InvalidMerchantQueryException(
                    "longitude는 " + MIN_LONGITUDE + "~" + MAX_LONGITUDE + " 사이여야 합니다.");
        }
        int radiusMeters = normalizeRadius(requestedRadiusMeters);
        if (!merchantCategoryMapper.existsMapVisibleCategory(categoryId)) {
            throw new MerchantCategoryNotFoundException("존재하지 않는 카테고리입니다. categoryId=" + categoryId);
        }

        List<MerchantListRow> categoryMerchants = merchantMapper.findActiveMerchantsByCategoryId(categoryId);
        if (categoryMerchants.isEmpty()) {
            return List.of();
        }
        if (merchantId != null && !merchantId.isBlank()) {
            categoryMerchants = filterToMerchant(categoryMerchants, categoryId, merchantId);
        }

        List<String> groupCodes = merchantCategoryMapper.findEnabledKakaoGroupCodes(categoryId);
        List<KakaoPlace> places = searchPlaces(groupCodes, categoryMerchants, latitude, longitude, radiusMeters);
        List<NearbyMerchantResponse> results = toNearbyMerchants(categoryMerchants, places);
        int placesFound = places.size();
        int matched = results.size();
        LOGGER.info(() -> "근처 가맹점 조회 categoryId=" + categoryId + " 전략=" + (groupCodes.isEmpty() ? "2안" : "1안")
                + " 카카오결과=" + placesFound + "건 매칭=" + matched + "건");
        return results;
    }

    private List<MerchantListRow> filterToMerchant(List<MerchantListRow> categoryMerchants, String categoryId,
                                                    String merchantId) {
        return categoryMerchants.stream()
                .filter(merchant -> merchant.merchantId().equals(merchantId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new MerchantNotFoundException(
                        "해당 카테고리에 존재하지 않는 가맹점입니다. categoryId=" + categoryId + " merchantId=" + merchantId));
    }

    private List<KakaoPlace> searchPlaces(List<String> groupCodes, List<MerchantListRow> categoryMerchants,
                                          double latitude, double longitude, int radiusMeters) {
        if (!groupCodes.isEmpty()) {
            return groupCodes.stream()
                    .flatMap(code -> kakaoLocalClient.searchByCategory(code, latitude, longitude, radiusMeters)
                            .stream())
                    .toList();
        }
        return categoryMerchants.stream()
                .flatMap(merchant -> kakaoLocalClient.searchByKeyword(merchant.name(), latitude, longitude,
                        radiusMeters).stream())
                .toList();
    }

    /**
     * 매칭되는 카카오 장소를 전부 응답으로 남긴다. merchantId는 브랜드 단위 식별자라 같은 브랜드의
     * 지점이 여러 곳이면(예: GS25 여러 지점) merchantId가 같은 응답이 여러 건 나올 수 있다.
     */
    private List<NearbyMerchantResponse> toNearbyMerchants(List<MerchantListRow> categoryMerchants,
                                                            List<KakaoPlace> places) {
        Set<String> categoryMerchantIds = categoryMerchants.stream()
                .map(MerchantListRow::merchantId)
                .collect(Collectors.toSet());

        MerchantCandidateSnapshot snapshot = merchantLookup.loadCandidates();
        List<NearbyMerchantResponse> results = new ArrayList<>();
        // 그룹코드를 여러 개 호출하는 카테고리(예: 의료=병원+약국)에서 같은 장소가 두 그룹코드에
        // 모두 잡히는 경우를 대비한 안전장치로만 쓴다(브랜드 단위 축소가 아니라 완전 동일 지점만 제거).
        Set<String> seenPlaces = new HashSet<>();
        for (KakaoPlace place : places) {
            String merchantId = snapshot.resolveMerchantId(place.placeName());
            boolean isKnownCategoryMerchant = merchantId != null && categoryMerchantIds.contains(merchantId);
            if (!isKnownCategoryMerchant) {
                LOGGER.fine(() -> "매칭 실패로 제외됨: place=" + place.placeName());
                continue;
            }
            String placeKey = merchantId + "|" + place.placeName() + "|" + place.latitude() + "|" + place.longitude();
            if (!seenPlaces.add(placeKey)) {
                continue;
            }
            results.add(new NearbyMerchantResponse(merchantId, place.placeName(), place.latitude(),
                    place.longitude(), place.distanceMeters(), place.address()));
        }

        return results.stream()
                .sorted(Comparator.comparing(NearbyMerchantResponse::distanceMeters,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private int normalizeRadius(Integer requestedRadiusMeters) {
        if (requestedRadiusMeters == null) {
            return DEFAULT_RADIUS_METERS;
        }
        if (requestedRadiusMeters < MIN_RADIUS_METERS || requestedRadiusMeters > MAX_RADIUS_METERS) {
            throw new InvalidMerchantQueryException(
                    "radiusMeters는 " + MIN_RADIUS_METERS + "~" + MAX_RADIUS_METERS + " 사이여야 합니다.");
        }
        return requestedRadiusMeters;
    }
}
