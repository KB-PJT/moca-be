package com.moca.mocabe.domain.benefit.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenefitRuleDefinitionParserTest {
  private final BenefitRuleDefinitionParser parser = new BenefitRuleDefinitionParser();

  @Test
  void parsesEverySupportedConditionAndNormalizesMissingCollections() {
    BenefitRuleDefinition definition =
        parser.parse(
            """
            {
              "schemaVersion": 1,
              "conditions": {
                "all": [
                  {"type":"PAYMENT_AMOUNT","operator":"GTE","value":"1000"},
                  {"type":"PREVIOUS_MONTH_SPEND","operator":"LT","value":"1000000"},
                  {"type":"USED_DAILY_COUNT","operator":"EQ","value":"0"},
                  {"type":"USED_MONTHLY_COUNT","operator":"LTE","value":"5"},
                  {"type":"MERCHANT","operator":"EQ","value":"merchant-id"},
                  {"type":"MERCHANT_CATEGORY","operator":"IN","values":["CAFE"]},
                  {"type":"TRANSACTION_TYPE","operator":"IN","values":["GIFT_CARD"]},
                  {"type":"DAY_OF_WEEK","operator":"IN","values":["FRIDAY"]},
                  {"type":"APPROVED_TIME","operator":"BETWEEN","values":["09:00","18:00"]},
                  {"type":"FOREIGN_TRANSACTION","operator":"EQ","value":"false"}
                ]
              },
              "reward": {
                "benefitType":"DISCOUNT",
                "rewardUnit":"KRW",
                "calculation":"RATE",
                "rate":"0.1"
              },
              "limits": [
                {"type":"TRANSACTION_BENEFIT_BASE","value":"50000"},
                {"type":"DAILY_USAGE_COUNT","value":"1"},
                {"type":"MONTHLY_USAGE_COUNT","value":"5"}
              ]
            }
            """);

    assertEquals(10, definition.conditions().all().size());
    assertEquals(List.of(), definition.conditions().any());
    assertEquals(List.of(), definition.conditions().none());
    assertEquals(3, definition.limits().size());

    BenefitRuleDefinition defaults =
        new BenefitRuleDefinition(
            1,
            null,
            new BenefitRuleDefinition.Reward(
                "POINT", "POINT", "FIXED", null, "1", null),
            null);
    BenefitRuleDefinition.Condition condition =
        new BenefitRuleDefinition.Condition("MERCHANT", "IN", null, null, null);
    assertEquals(List.of(), defaults.conditions().all());
    assertEquals(List.of(), defaults.limits());
    assertEquals(List.of(), condition.values());
  }

  @Test
  void acceptsInjectedObjectMapperAndAllRewardTypes() {
    BenefitRuleDefinitionParser injected = new BenefitRuleDefinitionParser(new ObjectMapper());

    assertEquals(
        "CASHBACK",
        injected.parse(minimal("CASHBACK", "KRW", "FIXED", null, "10", null))
            .reward()
            .benefitType());
    assertEquals(
        "POINT",
        parser.parse(minimal("POINT", "POINT", "PER_SPEND_UNIT", null, "1", "1000"))
            .reward()
            .benefitType());
    assertEquals(
        "MILEAGE",
        parser.parse(minimal("MILEAGE", "MILE", "PER_USAGE_UNIT", null, "1", null))
            .reward()
            .benefitType());
  }

  @Test
  void rejectsMalformedSchemaRewardAndNumbers() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("  "));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("{"));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(minimal(2, "DISCOUNT", "KRW", "RATE", "0.1", null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(minimal("UNKNOWN", "KRW", "RATE", "0.1", null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(minimal("DISCOUNT", "KRW", "RATE", "bad", null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(minimal("DISCOUNT", "KRW", "RATE", "-0.1", null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(minimal("DISCOUNT", "KRW", "RATE", null, null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(minimal("CASHBACK", "KRW", "FIXED", null, null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(minimal("POINT", "POINT", "PER_SPEND_UNIT", null, "1", "0")));
  }

  @Test
  void rejectsUnsupportedOrIncompleteConditions() {
    assertInvalidCondition("null");
    assertInvalidCondition(condition("UNKNOWN", "EQ", "1", null));
    assertInvalidCondition(condition("PAYMENT_AMOUNT", "IN", "1", null));
    assertInvalidCondition(condition("PAYMENT_AMOUNT", "GTE", null, null));
    assertInvalidCondition(condition("MERCHANT", "EQ", null, null));
    assertInvalidCondition(condition("MERCHANT", "IN", null, "[]"));
    assertInvalidCondition(condition("DAY_OF_WEEK", "IN", null, "[\"FUNDAY\"]"));
    assertInvalidCondition(condition("DAY_OF_WEEK", "EQ", null, "[\"FRIDAY\"]"));
    assertInvalidCondition(condition("APPROVED_TIME", "BETWEEN", null, "[\"09:00\"]"));
    assertInvalidCondition(
        condition("APPROVED_TIME", "BETWEEN", null, "[\"bad\",\"18:00\"]"));
    assertInvalidCondition(condition("FOREIGN_TRANSACTION", "EQ", "unknown", null));
    assertInvalidCondition(condition("NEW_MEMBER_GRACE", "IN", "true", null));
  }

  @Test
  void rejectsInvalidLimits() {
    assertInvalidLimit("null");
    assertInvalidLimit("{\"type\":\"UNKNOWN\",\"value\":\"1\"}");
    assertInvalidLimit("{\"type\":\"DAILY_USAGE_COUNT\",\"value\":\"-1\"}");
    assertInvalidLimit("{\"type\":\"DAILY_USAGE_COUNT\",\"value\":\"1.5\"}");
    assertInvalidLimit("{\"type\":\"MONTHLY_USAGE_COUNT\",\"value\":\"999999999999\"}");
  }

  private void assertInvalidCondition(String condition) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(withCondition(condition)));
  }

  private void assertInvalidLimit(String limit) {
    String json = minimal("DISCOUNT", "KRW", "RATE", "0.1", null, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(json.replace("\"limits\":[]", "\"limits\":[" + limit + "]")));
  }

  private String withCondition(String condition) {
    String json = minimal("DISCOUNT", "KRW", "RATE", "0.1", null, null);
    return json.replace("\"all\":[]", "\"all\":[" + condition + "]");
  }

  private String condition(String type, String operator, String value, String values) {
    String valueField = value == null ? "" : ",\"value\":\"" + value + "\"";
    String valuesField = values == null ? "" : ",\"values\":" + values;
    return "{\"type\":\"" + type + "\",\"operator\":\"" + operator + "\""
        + valueField + valuesField + "}";
  }

  private String minimal(
      String type,
      String unit,
      String calculation,
      String rate,
      String value,
      String spendUnit) {
    return minimal(1, type, unit, calculation, rate, value, spendUnit);
  }

  private String minimal(
      int schemaVersion,
      String type,
      String unit,
      String calculation,
      String rate,
      String value,
      String spendUnit) {
    return "{\"schemaVersion\":" + schemaVersion
        + ",\"conditions\":{\"all\":[],\"any\":[],\"none\":[]},\"reward\":{"
        + "\"benefitType\":\"" + type + "\",\"rewardUnit\":\"" + unit
        + "\",\"calculation\":\"" + calculation + "\""
        + optional("rate", rate) + optional("value", value)
        + optional("spendUnitAmount", spendUnit) + "},\"limits\":[]}";
  }

  private String optional(String key, String value) {
    return value == null ? "" : ",\"" + key + "\":\"" + value + "\"";
  }
}
