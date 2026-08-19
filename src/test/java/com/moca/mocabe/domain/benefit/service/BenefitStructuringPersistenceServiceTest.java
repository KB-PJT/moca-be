package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.mapper.BenefitStructuringMapper;
import com.moca.mocabe.domain.benefit.model.StructuredBenefitWrite;
import com.moca.mocabe.domain.benefit.structuring.ParsedTarget;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("혜택 구조화 저장 서비스")
class BenefitStructuringPersistenceServiceTest {
  @Test
  @DisplayName("새 category rule만 target과 함께 구조화한다")
  void persistsNewCategoryRule() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = write();
    when(mapper.updateOffer(write)).thenReturn(1);
    when(mapper.insertRule(write)).thenReturn(1);
    when(mapper.insertCategoryTarget(write)).thenReturn(1);

    boolean persisted = new BenefitStructuringPersistenceService(mapper).persist(
        write, new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, "CAFE"));

    assertTrue(persisted);
    verify(mapper).updateOffer(write);
    verify(mapper).insertCategoryTarget(write);
    verify(mapper).markStructured(write);
  }

  @Test
  @DisplayName("기존 rule은 덮어쓰거나 구조화 상태를 바꾸지 않는다")
  void doesNotOverwriteExistingRule() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = write();
    when(mapper.updateOffer(write)).thenReturn(1);
    when(mapper.insertRule(write)).thenReturn(0);

    boolean persisted = new BenefitStructuringPersistenceService(mapper).persist(
        write, new ParsedTarget(ParsedTarget.Type.ALL_MERCHANTS, "ALL"));

    assertFalse(persisted);
    verify(mapper).updateOffer(write);
    verify(mapper, never()).insertAllMerchantsTarget(write);
    verify(mapper, never()).markStructured(write);
  }

  @Test
  @DisplayName("대상 FK를 저장하지 못하면 구조화 완료로 표시하지 않고 실패한다")
  void failsWhenTargetIsNotPersisted() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = write();
    when(mapper.updateOffer(write)).thenReturn(1);
    when(mapper.insertRule(write)).thenReturn(1);
    when(mapper.insertCategoryTarget(write)).thenReturn(0);

    assertThrows(IllegalStateException.class, () -> new BenefitStructuringPersistenceService(mapper).persist(
        write, new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, "UNKNOWN")));

    verify(mapper, never()).markStructured(write);
  }

  @Test
  @DisplayName("선점 경쟁으로 offer를 갱신하지 못하면 신규 rule을 만들지 않는다")
  void doesNotInsertRuleWhenOfferWasNotUpdated() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = write();

    assertFalse(new BenefitStructuringPersistenceService(mapper).persist(
        write, new ParsedTarget(ParsedTarget.Type.ALL_MERCHANTS, "ALL")));

    verify(mapper, never()).insertRule(write);
  }

  @Test
  @DisplayName("명시 브랜드는 정확한 merchant target으로 저장한다")
  void persistsExplicitMerchantTarget() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = write();
    when(mapper.updateOffer(write)).thenReturn(1);
    when(mapper.insertRule(write)).thenReturn(1);
    when(mapper.insertMerchantTarget(write)).thenReturn(1);

    assertTrue(new BenefitStructuringPersistenceService(mapper).persist(
        write, new ParsedTarget(ParsedTarget.Type.MERCHANT, "GS25")));

    verify(mapper).insertMerchantTarget(write);
    verify(mapper).markStructured(write);
  }

  @Test
  @DisplayName("전 가맹점 target을 저장한다")
  void persistsAllMerchantsTarget() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = write();
    when(mapper.updateOffer(write)).thenReturn(1);
    when(mapper.insertRule(write)).thenReturn(1);
    when(mapper.insertAllMerchantsTarget(write)).thenReturn(1);

    assertTrue(new BenefitStructuringPersistenceService(mapper).persist(
        write, new ParsedTarget(ParsedTarget.Type.ALL_MERCHANTS, "ALL")));

    verify(mapper).insertAllMerchantsTarget(write);
  }

  @Test
  @DisplayName("단일 월 보상 한도는 policy와 tier를 함께 저장한다")
  void persistsMonthlyRewardLimit() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = writeWithMonthlyLimit();
    when(mapper.updateOffer(write)).thenReturn(1);
    when(mapper.insertRule(write)).thenReturn(1);
    when(mapper.insertCategoryTarget(write)).thenReturn(1);
    when(mapper.insertMonthlyRewardLimitPolicy(write)).thenReturn(1);
    when(mapper.insertMonthlyRewardLimitTier(write)).thenReturn(1);

    assertTrue(new BenefitStructuringPersistenceService(mapper).persist(
        write, new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, "CAFE")));

    verify(mapper).insertMonthlyRewardLimitPolicy(write);
    verify(mapper).insertMonthlyRewardLimitTier(write);
  }

  @Test
  @DisplayName("월 한도 policy 또는 tier 저장 실패 시 전체 작업을 실패시킨다")
  void failsWhenMonthlyRewardLimitIsIncomplete() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    StructuredBenefitWrite write = writeWithMonthlyLimit();
    when(mapper.updateOffer(write)).thenReturn(1);
    when(mapper.insertRule(write)).thenReturn(1);
    when(mapper.insertCategoryTarget(write)).thenReturn(1);
    when(mapper.insertMonthlyRewardLimitPolicy(write)).thenReturn(1);

    assertThrows(IllegalStateException.class, () -> new BenefitStructuringPersistenceService(mapper)
        .persist(write, new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, "CAFE")));

    when(mapper.insertMonthlyRewardLimitPolicy(write)).thenReturn(0);
    assertThrows(IllegalStateException.class, () -> new BenefitStructuringPersistenceService(mapper)
        .persist(write, new ParsedTarget(ParsedTarget.Type.MERCHANT_CATEGORY, "CAFE")));
  }

  private StructuredBenefitWrite write() {
    return new StructuredBenefitWrite("rule", "offer", "benefit", "카페 자동 구조화",
        "discount", "percentage",
        new BigDecimal("10"), "percent", new BigDecimal("300000"), null, null,
        "{\"schemaVersion\":1}", "CAFE", null, null);
  }

  private StructuredBenefitWrite writeWithMonthlyLimit() {
    return new StructuredBenefitWrite("rule", "offer", "benefit", "카페 자동 구조화",
        "discount", "percentage", new BigDecimal("10"), "percent", new BigDecimal("300000"),
        null, null, "{\"schemaVersion\":1}", "CAFE", "policy", new BigDecimal("10000"));
  }
}
