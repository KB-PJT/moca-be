package com.moca.mocabe.domain.merchant.mapper;

import com.moca.mocabe.domain.merchant.model.MerchantCardBenefitRuleRow;
import com.moca.mocabe.domain.merchant.model.MerchantDetailRow;
import com.moca.mocabe.domain.merchant.model.MerchantCategoryLineageRow;
import com.moca.mocabe.domain.merchant.model.MerchantBenefitTierRow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCardRecommendationMapper {
    MerchantDetailRow findActiveMerchant(@Param("merchantId") String merchantId);

    List<MerchantDetailRow> findActiveMerchants(@Param("merchantIds") List<String> merchantIds);

    MerchantDetailRow findCategoryTarget(@Param("merchantCategoryId") String merchantCategoryId,
                                         @Param("placeName") String placeName);

    List<String> findCategoryLineageIds(@Param("merchantCategoryId") String merchantCategoryId);

    List<MerchantCategoryLineageRow> findCategoryLineages(
            @Param("merchantIds") List<String> merchantIds);

    List<MerchantCardBenefitRuleRow> findOwnedCardBenefitRules(
            @Param("userId") String userId,
            @Param("merchantId") String merchantId,
            @Param("merchantCategoryId") String merchantCategoryId,
            @Param("merchantName") String merchantName,
            @Param("usageDate") LocalDate usageDate, @Param("performanceMonth") String performanceMonth);

    List<MerchantCardBenefitRuleRow> findOwnedCardBenefitRulesForMerchants(
            @Param("userId") String userId, @Param("merchantIds") List<String> merchantIds,
            @Param("usageDate") LocalDate usageDate, @Param("performanceMonth") String performanceMonth);

    List<MerchantBenefitTierRow> findBenefitTiersForOffers(
            @Param("offerIds") List<String> offerIds);
}
