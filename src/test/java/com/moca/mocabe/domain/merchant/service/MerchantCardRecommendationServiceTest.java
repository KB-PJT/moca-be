package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.mapper.MerchantCardRecommendationMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
import com.moca.mocabe.domain.merchant.model.MerchantDetailRow;
import com.moca.mocabe.domain.merchant.model.KakaoCategoryResolutionRule;
import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import com.moca.mocabe.domain.merchant.recommendation.CardBenefitEligibilityEvaluator;
import com.moca.mocabe.domain.merchant.recommendation.CardBenefitRankingStrategies;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.MerchantNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantCardRecommendationServiceTest {
    private final MerchantCardRecommendationMapper mapper = mock(MerchantCardRecommendationMapper.class);
    private final MerchantCategoryMapper categoryMapper = mock(MerchantCategoryMapper.class);
    private final MerchantLookup merchantLookup = mock(MerchantLookup.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final CardBenefitEligibilityEvaluator eligibilityEvaluator =
            mock(CardBenefitEligibilityEvaluator.class);
    private MerchantCardRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new MerchantCardRecommendationService(mapper, categoryMapper, merchantLookup,
                new KakaoBenefitCategoryResolver(), userMapper, eligibilityEvaluator,
                CardBenefitRankingStrategies.defaults(),
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("해당 가맹점 혜택 카드가 없으면 대표 추천 없이 빈 순위를 반환한다")
    void returnsEmptyRankingWithoutEligibleOwnedCard() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));

        var response = service.recommend("user-1", "merchant-1", null);

        assertNull(response.recommendedCard());
        assertEquals(List.of(), response.rankedCards());
        assertEquals(BenefitPreferenceType.IMMEDIATE_SAVINGS, response.benefitPreferenceType());
    }

    @Test
    @DisplayName("포인트 활용형은 예상 가치와 유형 가중치로 카드별 최적 혜택을 순위화한다")
    void ranksBestBenefitPerCardUsingPreference() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        when(userMapper.findBenefitPreferenceType("user-1")).thenReturn(BenefitPreferenceType.POINT_USAGE);
        List<MerchantCardBenefitCandidate> candidates = List.of(
                candidate("card-1", "할인 카드", "discount", "percent", "0.1", null, "300000", "120000", "1"),
                candidate("card-1", "할인 카드", "discount", "KRW", "1", null, "300000", "120000", "1"),
                candidate("card-2", "포인트 카드", "points", "point", "2", "1000", null, "0", "1.5"),
                candidate("card-3", "마일 카드", "points", "mile", "1", null, null, "0", "2"),
                candidate("card-4", "최소금액 카드", "discount", "percent", "50", null, null, "0", "1",
                        "20000"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026, 8, 10))))
                .thenReturn(candidates);

        var response = service.recommend("user-1", "merchant-1", new BigDecimal("10000"));

        assertEquals(3, response.rankedCards().size());
        assertEquals("card-2", response.recommendedCard().userCardId());
        assertEquals(new BigDecimal("30.00"), response.recommendedCard().estimatedValueKrw());
        assertEquals("card-1", response.rankedCards().get(1).userCardId());
        assertEquals(false, response.rankedCards().get(1).performanceMet());
        assertEquals(new BigDecimal("180000"),
                response.rankedCards().get(1).remainingPreviousSpendKrw());
        assertEquals(BigDecimal.ZERO, response.recommendedCard().remainingPreviousSpendKrw());
    }

    @Test
    @DisplayName("가맹점과 결제금액 입력을 검증한다")
    void validatesInput() {
        assertThrows(InvalidMerchantQueryException.class, () -> service.recommend("user", " ", null));
        when(mapper.findActiveMerchant("merchant")).thenReturn(
                new MerchantDetailRow("merchant", "가맹점", "CAFE", "카페"));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommend("user", "merchant", BigDecimal.ZERO));
        when(mapper.findActiveMerchant("missing")).thenReturn(null);
        assertThrows(MerchantNotFoundException.class,
                () -> service.recommend("user", "missing", BigDecimal.ONE));
    }

    @Test
    @DisplayName("여행형과 최대혜택형 등 모든 전략을 제공하며 알 수 없는 전략은 기본형을 사용한다")
    void providesAllStrategiesAndFallback() {
        var strategies = CardBenefitRankingStrategies.defaults();
        assertEquals(BenefitPreferenceType.TRAVEL_MILEAGE,
                strategies.get(BenefitPreferenceType.TRAVEL_MILEAGE).supports());
        assertEquals(BenefitPreferenceType.MAXIMUM_BENEFIT,
                strategies.get(BenefitPreferenceType.MAXIMUM_BENEFIT).supports());
        assertEquals(BenefitPreferenceType.IMMEDIATE_SAVINGS, strategies.get(null).supports());
    }

    @Test
    @DisplayName("스타벅스 장소는 Kakao category보다 merchant exact를 우선한다")
    void exactMerchantOverridesKakaoCategory() {
        when(merchantLookup.loadCandidates()).thenReturn(snapshot("starbucks-id", "스타벅스"));
        when(mapper.findActiveMerchant("starbucks-id"))
                .thenReturn(new MerchantDetailRow("starbucks-id", "스타벅스", "cafe-id", "CAFE", "카페"));

        var response = service.recommendPlace(
                "user-1", "스타벅스 강남점", "CE7", "음식점 > 카페", null);

        assertEquals("starbucks-id", response.merchant().merchantId());
    }

    @Test
    @DisplayName("merchant가 없는 일반 카페는 ALLOW Kakao category로만 추천한다")
    void fallsBackToAllowedKakaoCategory() {
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(null, null));
        when(categoryMapper.findBenefitResolutionRules("CE7")).thenReturn(List.of(
                new KakaoCategoryResolutionRule("cafe-id", "CAFE", "CE7", "",
                        "GROUP_CODE", new BigDecimal("0.990"), "ALLOW", 1)));
        when(mapper.findCategoryTarget("cafe-id", "동네카페"))
                .thenReturn(new MerchantDetailRow(null, "동네카페", "cafe-id", "CAFE", "카페"));

        var response = service.recommendPlace(
                "user-1", "동네카페", "CE7", "음식점 > 카페", null);

        assertEquals("CAFE", response.merchant().categoryCode());
        org.mockito.Mockito.verify(mapper).findOwnedCardBenefitRules(
                "user-1", null, "cafe-id", "동네카페",
                java.time.LocalDate.of(2026, 8, 10), "2026-07");
        org.mockito.Mockito.verify(eligibilityEvaluator).evaluate(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.990")),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDate.of(2026, 8, 10)));
    }

    @Test
    @DisplayName("DISPLAY_ONLY 또는 불명확 HP8 장소는 카드 추천을 반환하지 않는다")
    void rejectsDisplayOnlyOrUnknownPlace() {
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(null, null));
        when(categoryMapper.findBenefitResolutionRules("HP8")).thenReturn(List.of(
                new KakaoCategoryResolutionRule("dental-id", "DENTAL", "HP8", "치과",
                        "GROUP_AND_PATTERN", new BigDecimal("0.990"), "ALLOW", 1)));

        var response = service.recommendPlace(
                "user-1", "서울병원", "HP8", "의료,건강 > 병원", null);

        assertNull(response.recommendedCard());
        assertEquals(List.of(), response.rankedCards());
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .findCategoryTarget(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("장소 추천은 장소명과 결제금액을 검증한다")
    void validatesPlaceRecommendationInput() {
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommendPlace("user-1", " ", "CE7", "음식점 > 카페", null));

        when(merchantLookup.loadCandidates()).thenReturn(snapshot(null, null));
        when(categoryMapper.findBenefitResolutionRules("CE7")).thenReturn(List.of(
                new KakaoCategoryResolutionRule("cafe-id", "CAFE", "CE7", "",
                        "GROUP_CODE", BigDecimal.ONE, "ALLOW", 1)));
        when(mapper.findCategoryTarget("cafe-id", "동네카페"))
                .thenReturn(new MerchantDetailRow(null, "동네카페", "cafe-id", "CAFE", "카페"));

        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommendPlace(
                        "user-1", "동네카페", "CE7", "음식점 > 카페", BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Kakao 카테고리가 계산 가능해도 내부 추천 대상이 없으면 빈 결과를 반환한다")
    void returnsEmptyWhenResolvedCategoryTargetIsMissing() {
        when(merchantLookup.loadCandidates()).thenReturn(snapshot(null, null));
        when(categoryMapper.findBenefitResolutionRules("CE7")).thenReturn(List.of(
                new KakaoCategoryResolutionRule("cafe-id", "CAFE", "CE7", "",
                        "GROUP_CODE", BigDecimal.ONE, "ALLOW", 1)));
        when(mapper.findCategoryTarget("cafe-id", "동네카페")).thenReturn(null);

        var response = service.recommendPlace(
                "user-1", "동네카페", "CE7", "음식점 > 카페", BigDecimal.ONE);

        assertNull(response.recommendedCard());
        assertEquals(List.of(), response.rankedCards());
    }

    private MerchantCandidateSnapshot snapshot(String merchantId, String name) {
        List<MerchantNameCandidate> names = merchantId == null
                ? List.of() : List.of(new MerchantNameCandidate(merchantId, name));
        return new MerchantCandidateSnapshot(names, List.of(), new MerchantNameNormalizer());
    }

    private MerchantCardBenefitCandidate candidate(String cardId, String cardName, String rewardType,
                                                    String rewardUnit, String rewardValue, String basis,
                                                    String requiredSpend, String previousSpend, String conversion) {
        return candidate(cardId, cardName, rewardType, rewardUnit, rewardValue, basis, requiredSpend,
                previousSpend, conversion, null);
    }

    private MerchantCardBenefitCandidate candidate(String cardId, String cardName, String rewardType,
                                                    String rewardUnit, String rewardValue, String basis,
                                                    String requiredSpend, String previousSpend, String conversion,
                                                    String transactionMinimum) {
        return new MerchantCardBenefitCandidate("merchant-1", "이마트", "MART", "마트", cardId, cardName,
                "카드사", null, "마트 혜택", rewardType, rewardUnit, new BigDecimal(rewardValue),
                decimal(basis), decimal(transactionMinimum), decimal(requiredSpend), new BigDecimal(previousSpend),
                new BigDecimal(conversion));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
