package com.moca.mocabe.domain.benefit.mapper;

import com.moca.mocabe.domain.benefit.model.StructuredBenefitWrite;
import com.moca.mocabe.domain.benefit.model.RawBenefitStructuringCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 검증된 공통 parser 결과만 저장하는 구조화 전용 mapper다. */
@Mapper
public interface BenefitStructuringMapper {
  List<RawBenefitStructuringCandidate> findRawCandidates();
  int updateOffer(StructuredBenefitWrite write);
  int insertRule(StructuredBenefitWrite write);
  int insertCategoryTarget(StructuredBenefitWrite write);
  int insertMerchantTarget(StructuredBenefitWrite write);
  int insertAllMerchantsTarget(StructuredBenefitWrite write);
  int insertMonthlyRewardLimitPolicy(StructuredBenefitWrite write);
  int insertMonthlyRewardLimitTier(StructuredBenefitWrite write);
  int markStructured(StructuredBenefitWrite write);
  int markPartial(@Param("benefitId") String benefitId, @Param("note") String note);
}
