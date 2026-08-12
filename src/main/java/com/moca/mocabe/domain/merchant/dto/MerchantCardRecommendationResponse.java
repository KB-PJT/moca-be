package com.moca.mocabe.domain.merchant.dto;

import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import java.util.List;

public record MerchantCardRecommendationResponse(
        MerchantSummaryResponse merchant, BenefitPreferenceType benefitPreferenceType,
        RankedCardBenefitResponse recommendedCard, List<RankedCardBenefitResponse> rankedCards) { }
