package com.moca.mocabe.domain.merchant.recommendation;

import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import java.math.BigDecimal;
import java.util.Map;

/** 예상 원화 가치에 혜택 유형 선호 가중치를 적용한다. */
public class WeightedCardBenefitRankingStrategy implements CardBenefitRankingStrategy {
    private final BenefitPreferenceType preferenceType;
    private final Map<String, BigDecimal> weights;

    public WeightedCardBenefitRankingStrategy(BenefitPreferenceType preferenceType,
                                               Map<String, BigDecimal> weights) {
        this.preferenceType = preferenceType;
        this.weights = Map.copyOf(weights);
    }

    @Override
    public BenefitPreferenceType supports() {
        return preferenceType;
    }

    @Override
    public BigDecimal score(MerchantCardBenefitCandidate candidate, BigDecimal estimatedValueKrw) {
        return estimatedValueKrw.multiply(weights.getOrDefault(rewardKind(candidate), BigDecimal.ONE));
    }

    private String rewardKind(MerchantCardBenefitCandidate candidate) {
        if ("mile".equals(candidate.rewardUnit())) {
            return "mileage";
        }
        if ("point".equals(candidate.rewardUnit()) || "points".equals(candidate.rewardType())) {
            return "point";
        }
        return "immediate";
    }
}
