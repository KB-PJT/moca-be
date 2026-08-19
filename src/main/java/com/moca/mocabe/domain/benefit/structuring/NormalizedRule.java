package com.moca.mocabe.domain.benefit.structuring;

import java.util.List;
import java.util.Optional;

/** 원문 파서의 결과를 관계형 룰·JSON projection 생성 전 한 번만 조합하는 모델이다. */
public record NormalizedRule(
    String normalizedText,
    Optional<ParsedReward> reward,
    Optional<ParsedPerformanceTier> performanceTier,
    Optional<ParsedTransactionCondition> transactionCondition,
    List<ParsedLimit> limits,
    Optional<ParsedSchedule> schedule,
    Optional<ParsedTarget> target) {

  public NormalizedRule {
    reward = reward == null ? Optional.empty() : reward;
    performanceTier = performanceTier == null ? Optional.empty() : performanceTier;
    transactionCondition = transactionCondition == null ? Optional.empty() : transactionCondition;
    limits = limits == null ? List.of() : List.copyOf(limits);
    schedule = schedule == null ? Optional.empty() : schedule;
    target = target == null ? Optional.empty() : target;
  }
}
