package com.moca.mocabe.domain.merchant.recommendation;

import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
import com.moca.mocabe.domain.user.type.BenefitPreferenceType;
import java.math.BigDecimal;

/** 온보딩 혜택 성향별 카드 순위 계산 계약이다. */
public interface CardBenefitRankingStrategy {
    BenefitPreferenceType supports();
    BigDecimal score(MerchantCardBenefitCandidate candidate, BigDecimal estimatedValueKrw);
}
