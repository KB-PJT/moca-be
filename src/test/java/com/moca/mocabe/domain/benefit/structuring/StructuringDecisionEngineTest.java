package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StructuringDecisionEngineTest {
  private final StructuringDecisionEngine engine = new StructuringDecisionEngine();

  @Test
  void normalizesTextAndClassifiesEveryDecision() {
    BenefitTextNormalizer normalizer = new BenefitTextNormalizer();
    assertEquals("ABC", normalizer.compact(" <b>a b,c</b> "));

    NormalizedRule ambiguous = engine.analyze("설명만 있음", null, null);
    assertEquals(StructuringDecisionEngine.Decision.AMBIGUOUS_REWARD,
        engine.decide(ambiguous, false, false));

    NormalizedRule missingTarget = rule(Optional.empty());
    assertEquals(StructuringDecisionEngine.Decision.DATA_MISSING_TARGET,
        engine.decide(missingTarget, false, false));
    assertEquals(StructuringDecisionEngine.Decision.DATA_MISSING_TARGET,
        engine.decide(missingTarget, true, false));

    NormalizedRule ready = rule(Optional.of(
        new ParsedTarget(ParsedTarget.Type.ALL_MERCHANTS, "ALL")));
    assertEquals(StructuringDecisionEngine.Decision.CONDITIONAL_CONTEXT,
        engine.decide(ready, true, true));
    assertEquals(StructuringDecisionEngine.Decision.READY_FOR_PERSISTENCE,
        engine.decide(ready, true, false));
  }

  private NormalizedRule rule(Optional<ParsedTarget> target) {
    return new NormalizedRule("10% 할인",
        Optional.of(new ParsedReward(ParsedReward.Type.PERCENT, BigDecimal.TEN, "10% 할인")),
        Optional.empty(), Optional.empty(), List.of(), Optional.empty(), target);
  }
}
