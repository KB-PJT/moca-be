package com.moca.mocabe.domain.benefit.rule;

import java.util.List;

/** DB JSON 컬럼에 저장하는 버전형 카드 혜택 룰이다. */
public record BenefitRuleDefinition(
    int schemaVersion,
    ConditionSet conditions,
    Reward reward,
    List<Limit> limits) {

  public BenefitRuleDefinition {
    conditions = conditions == null ? new ConditionSet(List.of(), List.of(), List.of()) : conditions;
    limits = limits == null ? List.of() : List.copyOf(limits);
  }

  /** all은 AND, any는 OR, none은 하나라도 일치하면 제외한다. */
  public record ConditionSet(
      List<Condition> all,
      List<Condition> any,
      List<Condition> none) {

    public ConditionSet {
      all = all == null ? List.of() : List.copyOf(all);
      any = any == null ? List.of() : List.copyOf(any);
      none = none == null ? List.of() : List.copyOf(none);
    }
  }

  /** 값은 금액 정밀도를 보존하도록 문자열로 직렬화한다. */
  public record Condition(
      String type,
      String operator,
      String value,
      List<String> values,
      String rejectionReason) {

    public Condition {
      values = values == null ? List.of() : List.copyOf(values);
    }
  }

  /** 정률·정액·결제 단위·사용량 단위 계산에 필요한 값이다. */
  public record Reward(
      String benefitType,
      String rewardUnit,
      String calculation,
      String rate,
      String value,
      String spendUnitAmount) { }

  /** 현재 사용량은 JSON이 아니라 사용 원장에 저장하고 여기에는 정책값만 둔다. */
  public record Limit(String type, String value) { }
}
