package com.moca.mocabe.domain.benefit.mapper;

import com.moca.mocabe.domain.benefit.model.BenefitApprovalRow;
import com.moca.mocabe.domain.benefit.model.BenefitLimitTierCandidate;
import com.moca.mocabe.domain.benefit.model.SimpleBenefitRuleRow;
import com.moca.mocabe.domain.benefit.model.BenefitUsageCounts;
import com.moca.mocabe.domain.benefit.model.BenefitAreaSpendRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** CODEF 승인 후 계산 가능한 단순 룰·승인·사용 이력의 영속성 접근을 담당한다. */
@Mapper
public interface BenefitCalculationMapper {
  List<BenefitApprovalRow> findApprovalsForCalculation(
      @Param("approvalIds") List<String> approvalIds);

  List<String> findApprovalIdsForPeriod(
      @Param("userId") String userId,
      @Param("fromUtc") LocalDateTime fromUtc,
      @Param("toUtc") LocalDateTime toUtc);

  void deleteCalculationOutcomes(@Param("approvalIds") List<String> approvalIds);

  void deleteBenefitUsages(@Param("approvalIds") List<String> approvalIds);

  List<SimpleBenefitRuleRow> findSimpleRulesForUserCard(
      @Param("userCardId") String userCardId, @Param("usageDate") LocalDate usageDate);

  boolean hasBenefitOfferForUserCard(
      @Param("userCardId") String userCardId, @Param("offerName") String offerName);

  Integer findPreviousMonthSpend(
      @Param("userCardId") String userCardId, @Param("performanceMonth") String performanceMonth);

  Integer findCurrentMonthSpend(
      @Param("userCardId") String userCardId, @Param("performanceMonth") String performanceMonth);

  /** 같은 보유 카드의 계산을 직렬화해 공유 월 한도의 경쟁 조건을 막는다. */
  String lockUserCardForBenefitCalculation(@Param("userCardId") String userCardId);

  BenefitUsageCounts findConfirmedUsageCounts(
      @Param("userCardId") String userCardId,
      @Param("offerId") String offerId,
      @Param("usageDate") LocalDate usageDate,
      @Param("usageMonthStart") LocalDate usageMonthStart,
      @Param("nextMonthStart") LocalDate nextMonthStart);

  List<BenefitAreaSpendRow> findMonthlyBenefitAreaSpends(
      @Param("userCardId") String userCardId,
      @Param("areaGroupKey") String areaGroupKey,
      @Param("usageMonth") String usageMonth);

  List<String> findBenefitAreaKeysForApproval(
      @Param("approvalId") String approvalId, @Param("areaGroupKey") String areaGroupKey);

  int insertBenefitAreaSpendEventIfAbsent(
      @Param("approvalId") String approvalId,
      @Param("userCardId") String userCardId,
      @Param("areaGroupKey") String areaGroupKey,
      @Param("areaKey") String areaKey,
      @Param("usageMonth") String usageMonth,
      @Param("amountKrw") BigDecimal amountKrw);

  void upsertMonthlyBenefitAreaSpend(
      @Param("userCardId") String userCardId,
      @Param("areaGroupKey") String areaGroupKey,
      @Param("areaKey") String areaKey,
      @Param("usageMonth") String usageMonth,
      @Param("amountKrw") BigDecimal amountKrw);

  List<String> findApprovedApprovalIdsForCardMonth(
      @Param("userCardId") String userCardId, @Param("usageMonth") String usageMonth);

  void rebuildMonthlyBenefitAreaSpends(
      @Param("userCardId") String userCardId, @Param("usageMonth") String usageMonth);

  List<BenefitLimitTierCandidate> findMonthlyRewardLimitCandidates(
      @Param("offerId") String offerId,
      @Param("usageDate") LocalDate usageDate,
      @Param("limitUnit") String limitUnit);

  /** 현재 정책 또는 같은 shared_group_key에 이미 사용된 당월 보상값을 잠금 조회한다. */
  List<BigDecimal> findConfirmedMonthlyRewardsForUpdate(
      @Param("userCardId") String userCardId,
      @Param("limitPolicyId") String limitPolicyId,
      @Param("sharedGroupKey") String sharedGroupKey,
      @Param("usageMonthStart") LocalDate usageMonthStart,
      @Param("nextMonthStart") LocalDate nextMonthStart,
      @Param("limitUnit") String limitUnit);

  BigDecimal findMonthlyOfferRewardLimit(
      @Param("offerId") String offerId,
      @Param("usageDate") LocalDate usageDate,
      @Param("previousMonthSpend") BigDecimal previousMonthSpend,
      @Param("limitUnit") String limitUnit);

  /** Deep Dream 모두드림을 제외한 당월 추가 적립 사용량을 잠금 조회한다. */
  BigDecimal findConfirmedDeepDreamExtraRewardForUpdate(
      @Param("userCardId") String userCardId,
      @Param("usageMonthStart") LocalDate usageMonthStart,
      @Param("nextMonthStart") LocalDate nextMonthStart);

  BigDecimal findConfirmedMonthlyRewardForOfferForUpdate(
      @Param("userCardId") String userCardId,
      @Param("offerId") String offerId,
      @Param("usageMonthStart") LocalDate usageMonthStart,
      @Param("nextMonthStart") LocalDate nextMonthStart);

  String findApprovalMerchantNormalizedName(@Param("approvalId") String approvalId);

  BigDecimal findConfirmedMonthlyEligibleSpendForUpdate(
      @Param("userCardId") String userCardId,
      @Param("offerId") String offerId,
      @Param("usageMonthStart") LocalDate usageMonthStart,
      @Param("nextMonthStart") LocalDate nextMonthStart);

  void insertConfirmedUsage(
      @Param("usageId") String usageId,
      @Param("userCardId") String userCardId,
      @Param("offerId") String offerId,
      @Param("ruleId") String ruleId,
      @Param("limitPolicyId") String limitPolicyId,
      @Param("approvalId") String approvalId,
      @Param("usageDate") LocalDate usageDate,
      @Param("eligibleAmountKrw") BigDecimal eligibleAmountKrw,
      @Param("rewardAmountKrw") BigDecimal rewardAmountKrw,
      @Param("rewardOriginalValue") BigDecimal rewardOriginalValue,
      @Param("rewardOriginalUnit") String rewardOriginalUnit,
      @Param("approvedAt") LocalDateTime approvedAt);

  void insertCalculationOutcome(
      @Param("outcomeId") String outcomeId,
      @Param("userCardId") String userCardId,
      @Param("approvalId") String approvalId,
      @Param("offerId") String offerId,
      @Param("ruleId") String ruleId,
      @Param("limitPolicyId") String limitPolicyId,
      @Param("usageDate") LocalDate usageDate,
      @Param("rewardUnit") String rewardUnit,
      @Param("expectedRewardValue") BigDecimal expectedRewardValue,
      @Param("appliedRewardValue") BigDecimal appliedRewardValue,
      @Param("missedRewardValue") BigDecimal missedRewardValue,
      @Param("outcomeStatus") String outcomeStatus,
      @Param("rejectionReason") String rejectionReason);
}
