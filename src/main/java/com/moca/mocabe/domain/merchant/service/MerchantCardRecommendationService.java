package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.dto.MerchantCardRecommendationResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantCardRecommendationBatchResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantSummaryResponse;
import com.moca.mocabe.domain.merchant.dto.RecommendationReasonResponse;
import com.moca.mocabe.domain.merchant.dto.RankedCardBenefitResponse;
import com.moca.mocabe.domain.merchant.dto.BenefitTierResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantCardRecommendationMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitRuleRow;
import com.moca.mocabe.domain.merchant.model.MerchantBenefitTierRow;
import com.moca.mocabe.domain.merchant.model.MerchantDetailRow;
import com.moca.mocabe.domain.merchant.model.ResolvedKakaoCategory;
import com.moca.mocabe.domain.merchant.recommendation.CardBenefitEligibilityEvaluator;
import com.moca.mocabe.domain.merchant.recommendation.CardBenefitRankingStrategies;
import com.moca.mocabe.domain.merchant.recommendation.CardBenefitRankingStrategy;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import com.moca.mocabe.global.exception.merchant.InvalidMerchantQueryException;
import com.moca.mocabe.global.exception.merchant.MerchantNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 가맹점 카테고리에 적용되는 보유 카드 혜택을 온보딩 성향에 따라 정렬한다. */
public class MerchantCardRecommendationService {
    private static final BigDecimal DEFAULT_PAYMENT_AMOUNT = BigDecimal.ZERO;

    private final MerchantCardRecommendationMapper recommendationMapper;
    private final MerchantCategoryMapper merchantCategoryMapper;
    private final MerchantLookup merchantLookup;
    private final KakaoBenefitCategoryResolver categoryResolver;
    private final UserMapper userMapper;
    private final CardBenefitEligibilityEvaluator eligibilityEvaluator;
    private final CardBenefitRankingStrategies rankingStrategies;
    private final Clock clock;

    public MerchantCardRecommendationService(MerchantCardRecommendationMapper recommendationMapper,
                                             MerchantCategoryMapper merchantCategoryMapper,
                                             MerchantLookup merchantLookup,
                                             KakaoBenefitCategoryResolver categoryResolver,
                                             UserMapper userMapper,
                                             CardBenefitEligibilityEvaluator eligibilityEvaluator,
                                             CardBenefitRankingStrategies rankingStrategies,
                                             Clock clock) {
        this.recommendationMapper = recommendationMapper;
        this.merchantCategoryMapper = merchantCategoryMapper;
        this.merchantLookup = merchantLookup;
        this.categoryResolver = categoryResolver;
        this.userMapper = userMapper;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.rankingStrategies = rankingStrategies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MerchantCardRecommendationResponse recommend(String userId, String merchantId,
                                                        BigDecimal paymentAmount) {
        if (merchantId == null || merchantId.isBlank()) {
            throw new InvalidMerchantQueryException("merchantId는 필수입니다.");
        }
        BigDecimal normalizedAmount = paymentAmount == null ? DEFAULT_PAYMENT_AMOUNT : paymentAmount;
        if (paymentAmount != null && normalizedAmount.signum() <= 0) {
            throw new InvalidMerchantQueryException("paymentAmount는 0보다 커야 합니다.");
        }
        MerchantDetailRow merchant = recommendationMapper.findActiveMerchant(merchantId);
        if (merchant == null) {
            throw new MerchantNotFoundException("존재하지 않는 가맹점입니다. merchantId=" + merchantId);
        }

        return recommendTarget(userId, merchant, null, paymentAmount);
    }

    /** merchant exact를 먼저 사용하고, 실패한 장소만 검증된 Kakao category로 보수적으로 추천한다. */
    @Transactional(readOnly = true)
    public MerchantCardRecommendationResponse recommendPlace(String userId, String placeName,
                                                             String categoryGroupCode,
                                                             String categoryName,
                                                             BigDecimal paymentAmount) {
        if (placeName == null || placeName.isBlank()) {
            throw new InvalidMerchantQueryException("placeName은 필수입니다.");
        }
        String merchantId = merchantLookup.loadCandidates().resolveMerchantId(placeName);
        if (merchantId != null) {
            return recommend(userId, merchantId, paymentAmount);
        }

        var rules = merchantCategoryMapper.findBenefitResolutionRules(categoryGroupCode);
        ResolvedKakaoCategory resolved = categoryResolver.resolve(
                new KakaoPlace(placeName, 0, 0, null, null, categoryGroupCode, categoryName),
                rules, BigDecimal.ZERO);
        if (!resolved.calculable()) {
            return emptyResponse(userId, placeName);
        }
        MerchantDetailRow category = recommendationMapper.findCategoryTarget(
                resolved.merchantCategoryId(), placeName);
        if (category == null) {
            return emptyResponse(userId, placeName);
        }
        return recommendTarget(userId, category, resolved.confidence(), paymentAmount);
    }

    /** 목록 화면의 여러 가맹점을 세 번의 고정 Mapper 조회로 추천한다. */
    @Transactional(readOnly = true)
    public MerchantCardRecommendationBatchResponse recommendBatch(
            String userId, List<String> merchantIds, BigDecimal paymentAmount) {
        if (merchantIds == null || merchantIds.isEmpty() || merchantIds.size() > 50
                || merchantIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new InvalidMerchantQueryException("merchantIds는 1개 이상 50개 이하여야 합니다.");
        }
        List<String> distinctIds = merchantIds.stream().distinct().toList();
        BigDecimal amount = paymentAmount == null ? DEFAULT_PAYMENT_AMOUNT : paymentAmount;
        if (paymentAmount != null && amount.signum() <= 0) {
            throw new InvalidMerchantQueryException("paymentAmount는 0보다 커야 합니다.");
        }
        BenefitPreferenceType preference = preference(userId);
        LocalDate today = LocalDate.now(clock);
        String performanceMonth = YearMonth.from(today).minusMonths(1).toString();
        Map<String, MerchantDetailRow> merchants = recommendationMapper.findActiveMerchants(distinctIds)
                .stream().collect(java.util.stream.Collectors.toMap(MerchantDetailRow::merchantId, row -> row));
        Map<String, List<String>> lineages = recommendationMapper.findCategoryLineages(distinctIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.moca.mocabe.domain.merchant.model.MerchantCategoryLineageRow::merchantId,
                        java.util.stream.Collectors.mapping(
                                com.moca.mocabe.domain.merchant.model.MerchantCategoryLineageRow::merchantCategoryId,
                                java.util.stream.Collectors.toList())));
        Map<String, List<MerchantCardBenefitRuleRow>> rules = recommendationMapper
                .findOwnedCardBenefitRulesForMerchants(userId, distinctIds, today, performanceMonth).stream()
                .collect(java.util.stream.Collectors.groupingBy(MerchantCardBenefitRuleRow::merchantId));
        List<MerchantCardRecommendationResponse> responses = distinctIds.stream().map(id -> {
            MerchantDetailRow merchant = merchants.get(id);
            if (merchant == null) {
                return new MerchantCardRecommendationResponse(
                        new MerchantSummaryResponse(id, null, null, null), preference, null, List.of());
            }
            List<MerchantCardBenefitCandidate> candidates = eligibilityEvaluator.evaluate(
                    rules.getOrDefault(id, List.of()), id, lineages.getOrDefault(id, List.of()), null, today);
            List<RankedCardBenefitResponse> ranked = rank(candidates, amount, preference, today);
            return new MerchantCardRecommendationResponse(
                    new MerchantSummaryResponse(id, merchant.name(), merchant.categoryCode(),
                            merchant.categoryName()), preference, recommendedCard(ranked), ranked);
        }).toList();
        return new MerchantCardRecommendationBatchResponse(responses);
    }

    private MerchantCardRecommendationResponse recommendTarget(String userId, MerchantDetailRow merchant,
                                                               BigDecimal placeConfidence,
                                                               BigDecimal paymentAmount) {
        BigDecimal normalizedAmount = paymentAmount == null ? DEFAULT_PAYMENT_AMOUNT : paymentAmount;
        if (paymentAmount != null && normalizedAmount.signum() <= 0) {
            throw new InvalidMerchantQueryException("paymentAmount는 0보다 커야 합니다.");
        }
        BenefitPreferenceType preference = preference(userId);
        LocalDate today = LocalDate.now(clock);
        String performanceMonth = YearMonth.from(today).minusMonths(1).toString();
        List<String> categoryLineageIds = recommendationMapper.findCategoryLineageIds(
                merchant.merchantCategoryId());
        var ruleRows = recommendationMapper.findOwnedCardBenefitRules(
                userId, merchant.merchantId(), merchant.merchantCategoryId(), merchant.name(),
                today, performanceMonth);
        List<MerchantCardBenefitCandidate> candidates = eligibilityEvaluator.evaluate(
                ruleRows, merchant.merchantId(), categoryLineageIds, placeConfidence, today);
        List<RankedCardBenefitResponse> rankedCards = rank(candidates, normalizedAmount, preference, today);
        return new MerchantCardRecommendationResponse(
                new MerchantSummaryResponse(merchant.merchantId(), merchant.name(), merchant.categoryCode(),
                        merchant.categoryName()), preference,
                recommendedCard(rankedCards), rankedCards);
    }

    private MerchantCardRecommendationResponse emptyResponse(String userId, String placeName) {
        return new MerchantCardRecommendationResponse(
                new MerchantSummaryResponse(null, placeName, null, null), preference(userId), null, List.of());
    }

    private BenefitPreferenceType preference(String userId) {
        BenefitPreferenceType preference = userMapper.findBenefitPreferenceType(userId);
        return preference == null ? BenefitPreferenceType.IMMEDIATE_SAVINGS : preference;
    }

    private RankedCardBenefitResponse recommendedCard(List<RankedCardBenefitResponse> rankedCards) {
        return rankedCards.stream()
                .filter(this::isRecommendationEligible)
                .findFirst()
                .orElse(null);
    }

    private boolean isRecommendationEligible(RankedCardBenefitResponse card) {
        if (!card.performanceMet()) {
            return false;
        }
        return card.recommendationReasons().stream()
                .filter(reason -> "MINIMUM_PAYMENT".equals(reason.code()))
                .findFirst()
                .map(RecommendationReasonResponse::satisfied)
                .orElse(true);
    }

    private List<RankedCardBenefitResponse> rank(List<MerchantCardBenefitCandidate> candidates,
                                                 BigDecimal paymentAmount,
                                                 BenefitPreferenceType preference,
                                                 LocalDate usageDate) {
        CardBenefitRankingStrategy strategy = rankingStrategies.get(preference);
        Map<String, ScoredCandidate> bestByCard = new LinkedHashMap<>();
        for (MerchantCardBenefitCandidate candidate : candidates) {
            boolean minimumPaymentMet = candidate.transactionMinKrw() == null
                    || paymentAmount.compareTo(candidate.transactionMinKrw()) >= 0;
            BigDecimal estimated = estimateValue(candidate, paymentAmount);
            BigDecimal monthlyRemaining = monthlyRemaining(candidate);
            if (monthlyRemaining != null) {
                estimated = estimated.min(monthlyRemaining);
            }
            boolean performanceMet = candidate.previousSpendMinKrw() == null
                    || candidate.previousMonthSpendKrw().compareTo(candidate.previousSpendMinKrw()) >= 0;
            BigDecimal score = strategy.score(candidate, estimated);
            ScoredCandidate scored = new ScoredCandidate(
                    candidate, estimated, score, performanceMet, minimumPaymentMet);
            ScoredCandidate current = bestByCard.get(candidate.userCardId());
            if (current == null || isHigherPriority(scored, current)) {
                bestByCard.put(candidate.userCardId(), scored);
            }
        }
        Map<String, List<BenefitTierResponse>> tiersByOffer = benefitTiersByOffer(candidates, usageDate);
        List<ScoredCandidate> sorted = new ArrayList<>(bestByCard.values());
        sorted.sort(Comparator.comparing(ScoredCandidate::score).reversed()
                .thenComparing(Comparator.comparing(this::ratePriority).reversed()));
        List<RankedCardBenefitResponse> result = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            ScoredCandidate item = sorted.get(index);
            MerchantCardBenefitCandidate candidate = item.candidate();
            BigDecimal remainingPreviousSpend = remainingPreviousSpend(candidate);
            List<BenefitTierResponse> tiers = candidate.offerId() == null
                    ? List.of() : tiersByOffer.getOrDefault(candidate.offerId(), List.of());
            BenefitTierProgress tierProgress = tierProgress(candidate, candidates, tiers);
            BigDecimal monthlyRemaining = monthlyRemaining(candidate);
            result.add(new RankedCardBenefitResponse(index + 1, candidate.userCardId(), candidate.cardName(),
                    candidate.issuerName(), candidate.cardImageUrl(), candidate.offerName(), candidate.rewardType(),
                    candidate.rewardUnit(), candidate.rewardValue(), item.estimatedValueKrw(), paymentAmount,
                    candidate.transactionMinKrw(), candidate.previousMonthSpendKrw(),
                    candidate.previousSpendMinKrw(), remainingPreviousSpend,
                    tierProgress.currentTier(), tierProgress.nextTier(), tierProgress.targetAmount(),
                    tierProgress.currentTierAchieved(), tierProgress.remainingAmountToNextTier(),
                    candidate.monthlyLimitKrw(), candidate.monthlyUsedKrw(), monthlyRemaining,
                    item.performanceMet(),
                    recommendationReasons(candidate, item.performanceMet(), item.minimumPaymentMet(),
                            remainingPreviousSpend,
                            monthlyRemaining), tiers));
        }
        return List.copyOf(result);
    }

    private List<RecommendationReasonResponse> recommendationReasons(
            MerchantCardBenefitCandidate candidate, boolean performanceMet, boolean minimumPaymentMet,
            BigDecimal remainingPreviousSpend, BigDecimal monthlyRemaining) {
        List<RecommendationReasonResponse> reasons = new ArrayList<>(List.of(
                new RecommendationReasonResponse("MERCHANT_BENEFIT_MATCHED", true, null, null,
                        BigDecimal.ZERO),
                new RecommendationReasonResponse("PREVIOUS_SPEND", performanceMet,
                        candidate.previousMonthSpendKrw(), candidate.previousSpendMinKrw(),
                        remainingPreviousSpend),
                new RecommendationReasonResponse("MINIMUM_PAYMENT", minimumPaymentMet, null,
                        candidate.transactionMinKrw(), minimumPaymentMet ? BigDecimal.ZERO
                                : candidate.transactionMinKrw())));
        if (candidate.monthlyLimitKrw() != null) {
            reasons.add(new RecommendationReasonResponse("MONTHLY_LIMIT",
                    monthlyRemaining.signum() > 0, candidate.monthlyUsedKrw(),
                    candidate.monthlyLimitKrw(), monthlyRemaining));
        }
        return List.copyOf(reasons);
    }

    private BigDecimal monthlyRemaining(MerchantCardBenefitCandidate candidate) {
        if (candidate.monthlyLimitKrw() == null) {
            return null;
        }
        return candidate.monthlyLimitKrw().subtract(candidate.monthlyUsedKrw()).max(BigDecimal.ZERO);
    }

    private BigDecimal estimateValue(MerchantCardBenefitCandidate candidate, BigDecimal paymentAmount) {
        BigDecimal rawValue;
        if ("percent".equals(candidate.rewardUnit())) {
            rawValue = paymentAmount.multiply(candidate.rewardValue()).divide(new BigDecimal("100"), 2,
                    RoundingMode.DOWN);
        } else if (candidate.rewardBasisAmount() != null) {
            rawValue = paymentAmount.divide(candidate.rewardBasisAmount(), 0, RoundingMode.DOWN)
                    .multiply(candidate.rewardValue());
        } else {
            rawValue = candidate.rewardValue();
        }
        if ("point".equals(candidate.rewardUnit()) || "mile".equals(candidate.rewardUnit())) {
            return rawValue.multiply(candidate.krwPerRewardUnit()).setScale(2, RoundingMode.DOWN);
        }
        return rawValue.setScale(2, RoundingMode.DOWN);
    }

    private boolean isHigherPriority(ScoredCandidate candidate, ScoredCandidate current) {
        int scoreComparison = candidate.score().compareTo(current.score());
        return scoreComparison > 0
                || (scoreComparison == 0
                && ratePriority(candidate).compareTo(ratePriority(current)) > 0);
    }

    private BigDecimal ratePriority(ScoredCandidate scored) {
        MerchantCardBenefitCandidate candidate = scored.candidate();
        return "percent".equals(candidate.rewardUnit()) && candidate.rewardValue() != null
                ? candidate.rewardValue() : BigDecimal.ZERO;
    }

    private BigDecimal remainingPreviousSpend(MerchantCardBenefitCandidate candidate) {
        if (candidate.previousSpendMinKrw() == null) {
            return BigDecimal.ZERO;
        }
        return candidate.previousSpendMinKrw().subtract(candidate.previousMonthSpendKrw()).max(BigDecimal.ZERO);
    }

    /**
     * 카드 전체 실적 구간이 아니라, 현재 가맹점에 매칭된 동일 offer의 룰 구간만 사용한다.
     * 선택된 룰의 실적 기준이 현재 구간이고, 달성 뒤에는 다음 구간까지 남은 금액을 반환한다.
     */
    private Map<String, List<BenefitTierResponse>> benefitTiersByOffer(
            List<MerchantCardBenefitCandidate> candidates, LocalDate usageDate) {
        List<String> offerIds = candidates.stream().map(MerchantCardBenefitCandidate::offerId)
                .filter(Objects::nonNull).distinct().toList();
        if (offerIds.isEmpty()) {
            return Map.of();
        }
        List<MerchantBenefitTierRow> rows = recommendationMapper.findBenefitTiersForOffers(offerIds, usageDate);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        MerchantBenefitTierRow::offerId,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.mapping(
                                        row -> new BenefitTierResponse(row.position(),
                                                row.requiredPreviousSpendKrw(), row.monthlyLimitKrw()),
                                        java.util.stream.Collectors.toList()),
                                values -> values.stream()
                                        .sorted(java.util.Comparator.comparing(
                                                BenefitTierResponse::tier,
                                                java.util.Comparator.nullsLast(Integer::compareTo)))
                                        .toList())));
    }

    private BenefitTierProgress tierProgress(MerchantCardBenefitCandidate candidate,
                                             List<MerchantCardBenefitCandidate> candidates,
                                             List<BenefitTierResponse> allTiers) {
        List<BenefitTierResponse> performanceTiers = allTiers.stream()
                .filter(tier -> tier.requiredPreviousSpendKrw() != null)
                .toList();
        if (!performanceTiers.isEmpty()) {
            BigDecimal spend = candidate.previousMonthSpendKrw() == null
                    ? BigDecimal.ZERO : candidate.previousMonthSpendKrw();
            int achievedIndex = -1;
            for (int index = 0; index < performanceTiers.size(); index++) {
                if (spend.compareTo(performanceTiers.get(index).requiredPreviousSpendKrw()) >= 0) {
                    achievedIndex = index;
                }
            }
            int currentTier = achievedIndex < 0 ? 0 : performanceTiers.get(achievedIndex).tier();
            int nextIndex = achievedIndex + 1;
            BenefitTierResponse next = nextIndex < performanceTiers.size() ? performanceTiers.get(nextIndex) : null;
            BenefitTierResponse current = achievedIndex < 0 ? null : performanceTiers.get(achievedIndex);
            BigDecimal target = current == null ? (next == null ? null : next.requiredPreviousSpendKrw())
                    : current.requiredPreviousSpendKrw();
            BigDecimal remaining = next == null ? BigDecimal.ZERO
                    : next.requiredPreviousSpendKrw().subtract(spend).max(BigDecimal.ZERO);
            return new BenefitTierProgress(currentTier, next == null ? null : next.tier(), target,
                    current != null, remaining);
        }
        if (candidate.previousSpendMinKrw() == null) {
            return BenefitTierProgress.withoutPerformanceRequirement();
        }
        List<BigDecimal> targets = candidates.stream()
                .filter(other -> candidate.userCardId().equals(other.userCardId()))
                .filter(other -> sameOffer(candidate, other))
                .map(MerchantCardBenefitCandidate::previousSpendMinKrw)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        int targetIndex = targets.indexOf(candidate.previousSpendMinKrw());
        int currentTier = candidate.benefitTierPosition() == null
                ? targetIndex + 1 : candidate.benefitTierPosition();
        BigDecimal nextTarget = targetIndex + 1 < targets.size() ? targets.get(targetIndex + 1) : null;
        Integer nextTier = nextTarget == null ? null : nextTierNumber(candidate, candidates,
                nextTarget, currentTier + 1);
        BigDecimal currentSpend = candidate.previousMonthSpendKrw();
        boolean currentTierAchieved = currentSpend.compareTo(candidate.previousSpendMinKrw()) >= 0;
        BigDecimal remainingTarget = currentTierAchieved && nextTarget != null
                ? nextTarget : candidate.previousSpendMinKrw();
        return new BenefitTierProgress(currentTier, nextTier, candidate.previousSpendMinKrw(),
                currentTierAchieved, remainingTarget.subtract(currentSpend).max(BigDecimal.ZERO));
    }

    private boolean sameOffer(MerchantCardBenefitCandidate left, MerchantCardBenefitCandidate right) {
        if (left.offerId() != null || right.offerId() != null) {
            return Objects.equals(left.offerId(), right.offerId());
        }
        return Objects.equals(left.offerName(), right.offerName());
    }

    private Integer nextTierNumber(MerchantCardBenefitCandidate candidate,
                                   List<MerchantCardBenefitCandidate> candidates,
                                   BigDecimal nextTarget, int fallback) {
        return candidates.stream()
                .filter(other -> candidate.userCardId().equals(other.userCardId()))
                .filter(other -> sameOffer(candidate, other))
                .filter(other -> nextTarget.equals(other.previousSpendMinKrw()))
                .map(MerchantCardBenefitCandidate::benefitTierPosition)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(fallback);
    }

    private record ScoredCandidate(MerchantCardBenefitCandidate candidate, BigDecimal estimatedValueKrw,
                                   BigDecimal score, boolean performanceMet, boolean minimumPaymentMet) { }

    private record BenefitTierProgress(Integer currentTier, Integer nextTier, BigDecimal targetAmount,
                                       boolean currentTierAchieved,
                                       BigDecimal remainingAmountToNextTier) {
        private static BenefitTierProgress withoutPerformanceRequirement() {
            return new BenefitTierProgress(null, null, null, true, BigDecimal.ZERO);
        }
    }
}
