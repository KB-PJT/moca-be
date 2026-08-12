package com.moca.mocabe.domain.merchant.mapper;

import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitRuleRow;
import com.moca.mocabe.domain.merchant.model.MerchantDetailRow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCardRecommendationMapper {
    MerchantDetailRow findActiveMerchant(@Param("merchantId") String merchantId);

    MerchantDetailRow findCategoryTarget(@Param("merchantCategoryId") String merchantCategoryId,
                                         @Param("placeName") String placeName);

    List<String> findCategoryLineageIds(@Param("merchantCategoryId") String merchantCategoryId);

    List<MerchantCardBenefitRuleRow> findOwnedCardBenefitRules(
            @Param("userId") String userId,
            @Param("merchantId") String merchantId,
            @Param("merchantCategoryId") String merchantCategoryId,
            @Param("merchantName") String merchantName,
            @Param("usageDate") LocalDate usageDate, @Param("performanceMonth") String performanceMonth);
}
