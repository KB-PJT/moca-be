package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.mapper.BenefitStructuringMapper;
import com.moca.mocabe.domain.benefit.model.RawBenefitStructuringCandidate;
import com.moca.mocabe.domain.benefit.model.StructuredBenefitWrite;
import com.moca.mocabe.domain.benefit.structuring.NormalizedRule;
import com.moca.mocabe.domain.benefit.structuring.ParsedReward;
import com.moca.mocabe.domain.benefit.structuring.StructuringDecisionEngine;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 미구조화 원문을 공통 parser로 처리해 안전 후보만 저장한다. */
@Service
public class BenefitStructuringBatchService {
  private final BenefitStructuringMapper mapper;
  private final BenefitStructuringPersistenceService persistenceService;
  private final StructuringDecisionEngine decisionEngine = new StructuringDecisionEngine();

  public BenefitStructuringBatchService(
      BenefitStructuringMapper mapper, BenefitStructuringPersistenceService persistenceService) {
    this.mapper = mapper;
    this.persistenceService = persistenceService;
  }

  public int persistReadyCandidates() {
    int persisted = 0;
    for (RawBenefitStructuringCandidate candidate : mapper.findRawCandidates()) {
      NormalizedRule rule = decisionEngine.analyze(
          candidate.detailText(), candidate.summary(), candidate.title());
      if (decisionEngine.decide(rule, rule.target().isPresent(), false)
          != StructuringDecisionEngine.Decision.READY_FOR_PERSISTENCE) {
        continue;
      }
      if (!isSafelyPersistable(rule)) {
        mapper.markPartial(candidate.benefitId(), unsupportedReason(rule));
        continue;
      }
      if (persistenceService.persist(write(candidate, rule), rule.target().orElseThrow())) {
        persisted++;
      }
    }
    return persisted;
  }

  /**
   * 아직 저장 모델에 투영하지 않은 조건이 있으면 구조화 상태를 올리지 않는다.
   *
   * <p>횟수 한도, 시간/요일, 거래당 산정 기준금액 상한과 단일 월 보상 한도는 저장한다. 일·연 금액
   * 한도, 복수 월 한도, 공휴일 조건은 현재 모델에 투영할 수 없으므로 검토 대상으로 남긴다.
   */
  private boolean isSafelyPersistable(NormalizedRule rule) {
    boolean unsupportedAmount = rule.limits().stream().anyMatch(limit -> limit.type()
        == com.moca.mocabe.domain.benefit.structuring.ParsedLimit.Type.AMOUNT
        && limit.period() != com.moca.mocabe.domain.benefit.structuring.ParsedLimit.Period.MONTHLY);
    return !unsupportedAmount && monthlyLimits(rule).size() <= 1 && !rule.normalizedText().contains("공휴일");
  }

  private String unsupportedReason(NormalizedRule rule) {
    if (rule.normalizedText().contains("공휴일")) {
      return "자동 구조화 보류: 공휴일 정본 데이터 필요";
    }
    if (monthlyLimits(rule).size() > 1) {
      return "자동 구조화 보류: 복수 월 금액 한도 해석 필요";
    }
    return "자동 구조화 보류: 일·연 금액 한도 계산 모델 필요";
  }

  private StructuredBenefitWrite write(RawBenefitStructuringCandidate candidate, NormalizedRule rule) {
    ParsedReward reward = rule.reward().orElseThrow();
    return new StructuredBenefitWrite(
        UUID.randomUUID().toString(), candidate.offerId(), candidate.benefitId(),
        candidate.title() + " 자동 구조화", rewardType(reward), valueType(reward), reward.value(),
        reward.type() == ParsedReward.Type.PERCENT ? "percent" : rewardUnit(reward),
        rule.performanceTier().map(value -> value.minimumKrw()).orElse(null),
        rule.transactionCondition().map(value -> value.minimumPaymentKrw()).orElse(null),
        rule.transactionCondition().map(value -> value.maximumEligiblePaymentKrw()).orElse(null),
        new com.moca.mocabe.domain.benefit.structuring.NormalizedRuleJsonFactory().create(rule),
        rule.target().orElseThrow().code(),
        monthlyLimit(rule) == null ? null : UUID.randomUUID().toString(), monthlyLimit(rule));
  }

  private List<BigDecimal> monthlyLimits(NormalizedRule rule) {
    return rule.limits().stream()
        .filter(limit -> limit.type() == com.moca.mocabe.domain.benefit.structuring.ParsedLimit.Type.AMOUNT)
        .filter(limit -> limit.period() == com.moca.mocabe.domain.benefit.structuring.ParsedLimit.Period.MONTHLY)
        .map(com.moca.mocabe.domain.benefit.structuring.ParsedLimit::value)
        .toList();
  }

  private BigDecimal monthlyLimit(NormalizedRule rule) {
    List<BigDecimal> limits = monthlyLimits(rule);
    return limits.size() == 1 ? limits.get(0) : null;
  }

  private String rewardType(ParsedReward reward) {
    return switch (reward.type()) {
      case PERCENT, FIXED_KRW -> "discount";
      case CASHBACK -> "cashback";
      default -> "points";
    };
  }

  private String valueType(ParsedReward reward) {
    return reward.type() == ParsedReward.Type.PERCENT ? "percentage" : "fixed_amount";
  }

  private String rewardUnit(ParsedReward reward) {
    return reward.type() == ParsedReward.Type.MILEAGE ? "mile"
        : reward.type() == ParsedReward.Type.POINT ? "point" : "KRW";
  }
}
