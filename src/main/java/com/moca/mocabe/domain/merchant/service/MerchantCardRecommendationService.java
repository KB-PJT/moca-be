package com.moca.mocabe.domain.merchant.service;

import com.moca.mocabe.domain.merchant.dto.MerchantCardRecommendationResponse;
import com.moca.mocabe.domain.merchant.dto.MerchantSummaryResponse;
import com.moca.mocabe.domain.merchant.dto.RankedCardBenefitResponse;
import com.moca.mocabe.domain.merchant.mapper.MerchantCardRecommendationMapper;
import com.moca.mocabe.domain.merchant.mapper.MerchantCategoryMapper;
import com.moca.mocabe.domain.merchant.model.KakaoPlace;
import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
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
import org.springframework.transaction.annotation.Transactional;

/** 가맹점 카테고리에 적용되는 보유 카드 혜택을 온보딩 성향에 따라 정렬한다. */
public class MerchantCardRecommendationService {
    private static final BigDecimal DEFAULT_PAYMENT_AMOUNT = new BigDecimal("10000");

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
        if (normalizedAmount.signum() <= 0) {
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

    private MerchantCardRecommendationResponse recommendTarget(String userId, MerchantDetailRow merchant,
                                                               BigDecimal placeConfidence,
                                                               BigDecimal paymentAmount) {
        BigDecimal normalizedAmount = paymentAmount == null ? DEFAULT_PAYMENT_AMOUNT : paymentAmount;
        if (normalizedAmount.signum() <= 0) {
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
        List<RankedCardBenefitResponse> rankedCards = rank(candidates, normalizedAmount, preference);
        return new MerchantCardRecommendationResponse(
                new MerchantSummaryResponse(merchant.merchantId(), merchant.name(), merchant.categoryCode(),
                        merchant.categoryName()), preference,
                rankedCards.isEmpty() ? null : rankedCards.get(0), rankedCards);
    }

    private MerchantCardRecommendationResponse emptyResponse(String userId, String placeName) {
        return new MerchantCardRecommendationResponse(
                new MerchantSummaryResponse(null, placeName, null, null), preference(userId), null, List.of());
    }

    private BenefitPreferenceType preference(String userId) {
        BenefitPreferenceType preference = userMapper.findBenefitPreferenceType(userId);
        return preference == null ? BenefitPreferenceType.IMMEDIATE_SAVINGS : preference;
    }

    private List<RankedCardBenefitResponse> rank(List<MerchantCardBenefitCandidate> candidates,
                                                 BigDecimal paymentAmount,
                                                 BenefitPreferenceType preference) {
        CardBenefitRankingStrategy strategy = rankingStrategies.get(preference);
        Map<String, ScoredCandidate> bestByCard = new LinkedHashMap<>();
        for (MerchantCardBenefitCandidate candidate : candidates) {
            if (candidate.transactionMinKrw() != null
                    && paymentAmount.compareTo(candidate.transactionMinKrw()) < 0) {
                continue;
            }
            BigDecimal estimated = estimateValue(candidate, paymentAmount);
            boolean performanceMet = candidate.previousSpendMinKrw() == null
                    || candidate.previousMonthSpendKrw().compareTo(candidate.previousSpendMinKrw()) >= 0;
            BigDecimal score = strategy.score(candidate, estimated);
            if (!performanceMet) {
                score = score.multiply(new BigDecimal("0.25"));
            }
            ScoredCandidate scored = new ScoredCandidate(candidate, estimated, score, performanceMet);
            ScoredCandidate current = bestByCard.get(candidate.userCardId());
            if (current == null || scored.score().compareTo(current.score()) > 0) {
                bestByCard.put(candidate.userCardId(), scored);
            }
        }
        List<ScoredCandidate> sorted = new ArrayList<>(bestByCard.values());
        sorted.sort(Comparator.comparing(ScoredCandidate::score).reversed());
        List<RankedCardBenefitResponse> result = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            ScoredCandidate item = sorted.get(index);
            MerchantCardBenefitCandidate candidate = item.candidate();
            BigDecimal remainingPreviousSpend = remainingPreviousSpend(candidate);
            result.add(new RankedCardBenefitResponse(index + 1, candidate.userCardId(), candidate.cardName(),
                    candidate.issuerName(), candidate.cardImageUrl(), candidate.offerName(), candidate.rewardType(),
                    candidate.rewardUnit(), candidate.rewardValue(), item.estimatedValueKrw(),
                    candidate.previousMonthSpendKrw(), candidate.previousSpendMinKrw(), remainingPreviousSpend,
                    item.performanceMet()));
        }
        return List.copyOf(result);
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

    private BigDecimal remainingPreviousSpend(MerchantCardBenefitCandidate candidate) {
        if (candidate.previousSpendMinKrw() == null) {
            return BigDecimal.ZERO;
        }
        return candidate.previousSpendMinKrw().subtract(candidate.previousMonthSpendKrw()).max(BigDecimal.ZERO);
    }

    private record ScoredCandidate(MerchantCardBenefitCandidate candidate, BigDecimal estimatedValueKrw,
                                   BigDecimal score, boolean performanceMet) { }
}
