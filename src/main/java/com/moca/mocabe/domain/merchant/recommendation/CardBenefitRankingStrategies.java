package com.moca.mocabe.domain.merchant.recommendation;

import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CardBenefitRankingStrategies {
    private final Map<BenefitPreferenceType, CardBenefitRankingStrategy> strategies;

    public CardBenefitRankingStrategies(List<CardBenefitRankingStrategy> strategies) {
        this.strategies = new EnumMap<>(BenefitPreferenceType.class);
        strategies.forEach(strategy -> this.strategies.put(strategy.supports(), strategy));
    }

    public CardBenefitRankingStrategy get(BenefitPreferenceType preferenceType) {
        return strategies.getOrDefault(preferenceType, strategies.get(BenefitPreferenceType.IMMEDIATE_SAVINGS));
    }

    public static CardBenefitRankingStrategies defaults() {
        return new CardBenefitRankingStrategies(List.of(
                weighted(BenefitPreferenceType.IMMEDIATE_SAVINGS, "3", "2", "1"),
                weighted(BenefitPreferenceType.POINT_USAGE, "3", "3", "1"),
                weighted(BenefitPreferenceType.TRAVEL_MILEAGE, "1", "2", "3"),
                weighted(BenefitPreferenceType.MAXIMUM_BENEFIT, "1", "1", "1")));
    }

    private static CardBenefitRankingStrategy weighted(BenefitPreferenceType type, String immediate,
                                                        String point, String mileage) {
        return new WeightedCardBenefitRankingStrategy(type, Map.of(
                "immediate", new BigDecimal(immediate), "point", new BigDecimal(point),
                "mileage", new BigDecimal(mileage)));
    }
}
