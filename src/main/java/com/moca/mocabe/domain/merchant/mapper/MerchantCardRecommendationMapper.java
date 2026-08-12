package com.moca.mocabe.domain.merchant.mapper;

import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitCandidate;
import com.moca.mocabe.domain.merchant.model.MerchantDetailRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCardRecommendationMapper {
    MerchantDetailRow findActiveMerchant(@Param("merchantId") String merchantId);

    MerchantDetailRow findCategoryTarget(@Param("merchantCategoryId") String merchantCategoryId,
                                         @Param("placeName") String placeName);

    List<MerchantCardBenefitCandidate> findEligibleOwnedCardBenefits(
            @Param("userId") String userId,
            @Param("merchantId") String merchantId,
            @Param("merchantCategoryId") String merchantCategoryId,
            @Param("merchantName") String merchantName,
            @Param("placeConfidence") BigDecimal placeConfidence,
            @Param("usageDate") LocalDate usageDate, @Param("performanceMonth") String performanceMonth);
}
