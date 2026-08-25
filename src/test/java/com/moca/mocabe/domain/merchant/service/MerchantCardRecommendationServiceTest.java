package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.merchant.mapper.MerchantCardRecommendationMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
import com.moca.mocabe.domain.merchant.model.MerchantBenefitTierRow;
import com.moca.mocabe.domain.merchant.model.MerchantDetailRow;
import com.moca.mocabe.domain.merchant.model.MerchantCategoryLineageRow;
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
import java.time.LocalDate;
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
    @DisplayName("결제금액을 생략하면 0원을 기준으로 예상 혜택을 계산한다")
    void defaultsMissingPaymentAmountToZero() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(candidateWithoutMonthlyLimit()));

        var card = service.recommend("user-1", "merchant-1", null).recommendedCard();

        assertEquals(BigDecimal.ZERO, card.estimatedPaymentAmountKrw());
        assertEquals(0, card.estimatedValueKrw().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("0원 기준으로 정률 혜택이 동률이면 높은 혜택률을 우선한다")
    void prefersHigherRateWhenZeroAmountMakesEstimatedValuesEqual() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                        candidate("card-5", "5% 카드", "discount", "percent", "5", null,
                                null, "0", "1"),
                        candidate("card-10", "10% 카드", "discount", "percent", "10", null,
                                null, "0", "1"),
                        candidate("card-tie", "10% 카드", "discount", "percent", "10", null,
                                null, "0", "1"),
                        candidate("card-tie", "5% 카드", "discount", "percent", "5", null,
                                null, "0", "1"),
                        candidate("card-state", "조건 없음", "discount", "percent", "10", null,
                                null, "0", "1"),
                        candidate("card-state", "최소금액 조건", "discount", "percent", "10", null,
                                null, "0", "1", "20000"),
                        candidate("card-performance", "실적 충족", "discount", "percent", "10", null,
                                null, "0", "1"),
                        candidate("card-performance", "실적 미충족", "discount", "percent", "10", null,
                                "300000", "0", "1")));

        var response = service.recommend("user-1", "merchant-1", null);

        assertEquals("card-10", response.recommendedCard().userCardId());
        assertEquals(new BigDecimal("10"), response.rankedCards().stream()
                .filter(card -> "card-tie".equals(card.userCardId()))
                .findFirst().orElseThrow().rewardValue());
        assertTrue(response.rankedCards().stream()
                .anyMatch(card -> "card-5".equals(card.userCardId())));
    }

    @Test
    @DisplayName("미충족 카드가 1위여도 충족 카드 중 가장 높은 순위를 대표 추천한다")
    void recommendsHighestRankedSatisfiedCard() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                        candidate("card-unmet", "미충족 카드", "discount", "percent", "20", null,
                                "300000", "0", "1"),
                        candidate("card-met", "충족 카드", "discount", "percent", "5", null,
                                null, "0", "1")));

        var response = service.recommend("user-1", "merchant-1", null);

        assertEquals("card-met", response.recommendedCard().userCardId());
        assertTrue(response.rankedCards().stream()
                .anyMatch(card -> "card-unmet".equals(card.userCardId()) && !card.performanceMet()));
    }

    @Test
    @DisplayName("모든 카드가 미충족이면 대표 추천 없이 순위만 반환한다")
    void returnsNoRecommendationWhenAllCardsAreUnmet() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                        candidate("card-performance", "실적 미충족", "discount", "percent", "20", null,
                                "300000", "0", "1"),
                        candidate("card-minimum", "최소금액 미충족", "discount", "percent", "10", null,
                                null, "0", "1", "20000")));

        var response = service.recommend("user-1", "merchant-1", null);

        assertNull(response.recommendedCard());
        assertEquals(2, response.rankedCards().size());
    }

    @Test
    @DisplayName("목록 배치는 가맹점 수와 무관하게 세 조회로 추천 가능 여부를 반환한다")
    void recommendsMerchantBatchWithoutNPlusOne() {
        when(mapper.findActiveMerchants(List.of("m-1", "missing"))).thenReturn(List.of(
                new MerchantDetailRow("m-1", "이마트", "category-1", "MART", "마트")));
        when(mapper.findCategoryLineages(List.of("m-1", "missing"))).thenReturn(List.of(
                new MerchantCategoryLineageRow("m-1", "category-1")));
        when(mapper.findOwnedCardBenefitRulesForMerchants(
                "user-1", List.of("m-1", "missing"), java.time.LocalDate.of(2026, 8, 10), "2026-07"))
                .thenReturn(List.of());
        when(eligibilityEvaluator.evaluate(
                List.of(), "m-1", List.of("category-1"), null,
                java.time.LocalDate.of(2026, 8, 10))).thenReturn(List.of(candidateWithoutMonthlyLimit()));

        var response = service.recommendBatch(
                "user-1", List.of("m-1", "m-1", "missing"), null);

        assertEquals(2, response.recommendations().size());
        assertEquals("이마트", response.recommendations().get(0).merchant().name());
        assertEquals("card-no-limit", response.recommendations().get(0).recommendedCard().userCardId());
        assertNull(response.recommendations().get(1).recommendedCard());
    }

    @Test
    @DisplayName("목록 배치는 빈 목록·최대 개수 초과·빈 식별자·0원 결제를 거절한다")
    void validatesMerchantBatch() {
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommendBatch("user", List.of(), null));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommendBatch("user", null, null));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommendBatch("user", java.util.Collections.nCopies(51, "m"), null));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommendBatch("user", java.util.Arrays.asList("m", null), null));
        assertThrows(InvalidMerchantQueryException.class,
                () -> service.recommendBatch("user", List.of("m"), BigDecimal.ZERO));
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
                candidate("card-5", "캐시백 카드", "cashback", "KRW", "1", null, null, "0", "1"),
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

        assertEquals(5, response.rankedCards().size());
        assertEquals("card-2", response.recommendedCard().userCardId());
        assertEquals(new BigDecimal("30.00"), response.recommendedCard().estimatedValueKrw());
        var card1 = response.rankedCards().stream()
                .filter(card -> "card-1".equals(card.userCardId())).findFirst().orElseThrow();
        assertEquals(false, card1.performanceMet());
        assertEquals(new BigDecimal("180000"), card1.remainingPreviousSpendKrw());
        assertEquals(BigDecimal.ZERO, response.recommendedCard().remainingPreviousSpendKrw());
        assertEquals(new BigDecimal("10000"), response.recommendedCard().estimatedPaymentAmountKrw());
        assertEquals("card-4", response.rankedCards().get(4).userCardId());
        assertEquals(false, response.rankedCards().get(4).recommendationReasons().stream()
                .filter(reason -> "MINIMUM_PAYMENT".equals(reason.code()))
                .findFirst().orElseThrow().satisfied());
        assertEquals("MERCHANT_BENEFIT_MATCHED",
                response.recommendedCard().recommendationReasons().get(0).code());
        assertEquals(true, response.rankedCards().stream()
                .anyMatch(card -> "cashback".equals(card.rewardType())));
    }

    @Test
    @DisplayName("전월 실적 경계는 충족하고 월 한도 소진 경계는 잔여 0으로 표시한다")
    void handlesPerformanceAndMonthlyLimitBoundaries() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        MerchantCardBenefitCandidate boundary = new MerchantCardBenefitCandidate(
                "merchant-1", "이마트", "MART", "마트", "card-boundary", "경계 카드",
                "카드사", null, "마트 할인", "discount", "percent", BigDecimal.ONE,
                null, null, new BigDecimal("300000"), new BigDecimal("300000"),
                BigDecimal.ONE, new BigDecimal("10000"), new BigDecimal("10000"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(boundary));

        var card = service.recommend("user-1", "merchant-1", new BigDecimal("10000"))
                .rankedCards().get(0);

        assertEquals(true, card.performanceMet());
        assertEquals(BigDecimal.ZERO, card.estimatedValueKrw());
        assertEquals(BigDecimal.ZERO, card.monthlyRemainingKrw());
        assertEquals(false, card.recommendationReasons().get(3).satisfied());
    }

    @Test
    @DisplayName("예상 혜택은 남은 월 금액 한도를 초과하지 않는다")
    void capsEstimatedValueAtRemainingMonthlyLimit() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        MerchantCardBenefitCandidate limited = new MerchantCardBenefitCandidate(
                "merchant-1", "이마트", "MART", "마트", "card-limited", "한도 카드",
                "카드사", null, "마트 할인", "discount", "percent", new BigDecimal("10"),
                null, null, null, BigDecimal.ZERO, BigDecimal.ONE,
                new BigDecimal("5000"), new BigDecimal("4500"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(limited));

        var card = service.recommend("user-1", "merchant-1", new BigDecimal("10000"))
                .rankedCards().get(0);

        assertEquals(new BigDecimal("500"), card.estimatedValueKrw());
        assertEquals(new BigDecimal("500"), card.monthlyRemainingKrw());
    }

    @Test
    @DisplayName("가맹점 혜택 룰의 실적 구간으로 게이지 정보를 반환한다")
    void returnsMerchantBenefitSpecificTierProgress() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        List<MerchantCardBenefitCandidate> tiers = List.of(
                candidate("card-tier", "구간 카드", "discount", "percent", "10", null,
                        "300000", "245000", "1", "offer-tier", 1),
                candidate("card-tier", "구간 카드", "discount", "percent", "1", null,
                        "500000", "245000", "1", "offer-tier", 2));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(tiers);
        when(mapper.findBenefitTiersForOffers(List.of("offer-tier"), LocalDate.of(2026, 8, 10))).thenReturn(
                List.of(new MerchantBenefitTierRow("offer-tier", 2, new BigDecimal("500000"),
                                new BigDecimal("10000")),
                        new MerchantBenefitTierRow("offer-tier", 1, new BigDecimal("200000"),
                                new BigDecimal("5000"))));

        var card = service.recommend("user-1", "merchant-1", new BigDecimal("10000"))
                .rankedCards().get(0);

        assertEquals(1, card.currentTier());
        assertEquals(2, card.nextTier());
        assertEquals(new BigDecimal("200000"), card.currentTierTargetAmount());
        assertEquals(true, card.isCurrentTierAchieved());
        assertEquals(new BigDecimal("255000"), card.remainingAmountToNextTier());
        assertEquals(2, card.tiers().size());
        assertEquals(1, card.tiers().get(0).tier());
        assertEquals(new BigDecimal("200000"), card.tiers().get(0).requiredPreviousSpendKrw());
    }

    @Test
    @DisplayName("최고 실적 구간을 달성한 가맹점 혜택은 다음 구간 없이 잔여 0원을 반환한다")
    void returnsNoNextTierForAchievedHighestMerchantBenefitTier() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        List<MerchantCardBenefitCandidate> tiers = List.of(
                candidate("card-top-tier", "구간 카드", "discount", "percent", "1", null,
                        "300000", "600000", "1", "offer-tier", 1),
                candidate("card-top-tier", "구간 카드", "discount", "percent", "10", null,
                        "500000", "600000", "1", "offer-tier", 2));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(tiers);

        var card = service.recommend("user-1", "merchant-1", new BigDecimal("10000"))
                .rankedCards().get(0);

        assertEquals(2, card.currentTier());
        assertNull(card.nextTier());
        assertEquals(new BigDecimal("500000"), card.currentTierTargetAmount());
        assertEquals(true, card.isCurrentTierAchieved());
        assertEquals(BigDecimal.ZERO, card.remainingAmountToNextTier());
    }

    @Test
    @DisplayName("실적 조건 없는 가맹점 혜택은 게이지 미노출 값을 반환한다")
    void returnsNoTierProgressWithoutPreviousSpendRequirement() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                        candidate("card-no-tier", "실적 없음 카드", "discount", "percent", "5", null,
                                null, "0", "1")));

        var card = service.recommend("user-1", "merchant-1", new BigDecimal("10000"))
                .recommendedCard();

        assertNull(card.currentTier());
        assertNull(card.nextTier());
        assertNull(card.currentTierTargetAmount());
        assertEquals(true, card.isCurrentTierAchieved());
        assertEquals(BigDecimal.ZERO, card.remainingAmountToNextTier());
    }

    @Test
    @DisplayName("tier 메타데이터가 없는 기존 후보도 후보 목록에서 다음 구간을 계산한다")
    void fallsBackToCandidateTiersWhenMetadataIsUnavailable() {
        when(mapper.findActiveMerchant("merchant-1"))
                .thenReturn(new MerchantDetailRow("merchant-1", "이마트", "MART", "마트"));
        List<MerchantCardBenefitCandidate> candidates = List.of(
                candidate("card-fallback", "구간 카드", "discount", "percent", "10", null,
                        "300000", "245000", "1", null, null, 1),
                candidate("card-fallback", "구간 카드", "discount", "percent", "10", null,
                        "500000", "245000", "1", null, null, 2));
        when(eligibilityEvaluator.evaluate(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("merchant-1"),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(candidates);

        var card = service.recommend("user-1", "merchant-1", new BigDecimal("10000"))
                .rankedCards().get(0);

        assertEquals(1, card.currentTier());
        assertEquals(2, card.nextTier());
        assertEquals(new BigDecimal("300000"), card.currentTierTargetAmount());
        assertEquals(false, card.isCurrentTierAchieved());
        assertEquals(new BigDecimal("55000"), card.remainingAmountToNextTier());
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
                previousSpend, conversion, null, null, null);
    }

    private MerchantCardBenefitCandidate candidate(String cardId, String cardName, String rewardType,
                                                    String rewardUnit, String rewardValue, String basis,
                                                    String requiredSpend, String previousSpend, String conversion,
                                                    String transactionMinimum) {
        return candidate(cardId, cardName, rewardType, rewardUnit, rewardValue, basis, requiredSpend,
                previousSpend, conversion, transactionMinimum, null, null);
    }

    private MerchantCardBenefitCandidate candidate(String cardId, String cardName, String rewardType,
                                                    String rewardUnit, String rewardValue, String basis,
                                                    String requiredSpend, String previousSpend, String conversion,
                                                    String offerId, Integer tierPosition) {
        return candidate(cardId, cardName, rewardType, rewardUnit, rewardValue, basis, requiredSpend,
                previousSpend, conversion, null, offerId, tierPosition);
    }

    private MerchantCardBenefitCandidate candidate(String cardId, String cardName, String rewardType,
                                                    String rewardUnit, String rewardValue, String basis,
                                                    String requiredSpend, String previousSpend, String conversion,
                                                    String transactionMinimum, String offerId,
                                                    Integer tierPosition) {
        return new MerchantCardBenefitCandidate("merchant-1", "이마트", "MART", "마트", cardId, cardName,
                "카드사", null, "마트 혜택", rewardType, rewardUnit, new BigDecimal(rewardValue),
                decimal(basis), decimal(transactionMinimum), decimal(requiredSpend), new BigDecimal(previousSpend),
                new BigDecimal(conversion), new BigDecimal("10000"), BigDecimal.ZERO, offerId, tierPosition);
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private MerchantCardBenefitCandidate candidateWithoutMonthlyLimit() {
        return new MerchantCardBenefitCandidate(
                "m-1", "이마트", "MART", "마트", "card-no-limit", "한도 없음",
                "카드사", null, "기본 할인", "discount", "percent", BigDecimal.ONE,
                null, null, null, BigDecimal.ZERO, BigDecimal.ONE, null, BigDecimal.ZERO);
    }
}
