package com.moca.mocabe.domain.benefit.structuring;

/** 반복 parser를 조합하되 target과 제외 조건이 확정되기 전에는 READY를 선언하지 않는다. */
public class StructuringDecisionEngine {
  private final BenefitTextNormalizer normalizer = new BenefitTextNormalizer();
  private final RewardParser rewardParser = new RewardParser();
  private final PerformanceParser performanceParser = new PerformanceParser();
  private final TransactionConditionParser transactionParser = new TransactionConditionParser();
  private final LimitParser limitParser = new LimitParser();
  private final ScheduleParser scheduleParser = new ScheduleParser();
  private final TargetParser targetParser = new TargetParser();

  public NormalizedRule analyze(String detailText, String summary, String title) {
    return new NormalizedRule(
        normalizer.normalize(detailText, summary, title),
        rewardParser.parse(detailText, summary, title),
        performanceParser.parse(detailText, summary, title),
        transactionParser.parse(detailText, summary, title),
        limitParser.parse(detailText, summary, title),
        scheduleParser.parse(detailText, summary, title),
        targetParser.parse(detailText, summary, title));
  }

  public Decision decide(NormalizedRule rule, boolean targetResolved, boolean contextRequired) {
    if (rule.reward().isEmpty()) {
      return Decision.AMBIGUOUS_REWARD;
    }
    if (!targetResolved || rule.target().isEmpty()) {
      return Decision.DATA_MISSING_TARGET;
    }
    return contextRequired ? Decision.CONDITIONAL_CONTEXT : Decision.READY_FOR_PERSISTENCE;
  }

  public enum Decision { READY_FOR_PERSISTENCE, CONDITIONAL_CONTEXT, DATA_MISSING_TARGET, AMBIGUOUS_REWARD }
}
