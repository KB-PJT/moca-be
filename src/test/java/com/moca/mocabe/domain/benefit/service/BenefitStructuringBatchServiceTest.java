package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.mapper.BenefitStructuringMapper;
import com.moca.mocabe.domain.benefit.model.RawBenefitStructuringCandidate;
import com.moca.mocabe.domain.benefit.model.StructuredBenefitWrite;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@DisplayName("혜택 구조화 batch")
class BenefitStructuringBatchServiceTest {
  @Test
  @DisplayName("산식에 완전히 투영 가능한 후보만 저장한다")
  void persistsOnlyReadyCandidates() {
    BenefitStructuringMapper mapper = Mockito.mock(BenefitStructuringMapper.class);
    BenefitStructuringPersistenceService persistence = Mockito.mock(BenefitStructuringPersistenceService.class);
    when(mapper.findRawCandidates()).thenReturn(List.of(
        new RawBenefitStructuringCandidate("benefit", "offer", "편의점", null, "모든 편의점 10% 할인"),
        new RawBenefitStructuringCandidate("limited", "offer2", "카페", null, "카페 10% 할인 월 2회 평일 09:00~18:00"),
        new RawBenefitStructuringCandidate(
            "base-capped", "offer-base", "카페", null, "카페 결제금액 1만원까지 10% 할인"),
        new RawBenefitStructuringCandidate("amount-limit", "offer3", "카페", null, "카페 10% 할인 월 최대 1만원"),
        new RawBenefitStructuringCandidate("fixed", "offer-fixed", "카페", null, "카페 1천원 할인"),
        new RawBenefitStructuringCandidate("cashback", "offer-cash", "카페", null, "카페 1천원 캐시백"),
        new RawBenefitStructuringCandidate("point", "offer-point", "카페", null, "카페 100P 적립"),
        new RawBenefitStructuringCandidate("mile", "offer-mile", "카페", null, "카페 100마일 적립"),
        new RawBenefitStructuringCandidate(
            "daily-limit", "offer-day", "카페", null, "카페 10% 할인 일 최대 1천원"),
        new RawBenefitStructuringCandidate(
            "multiple-monthly", "offer-months", "카페", null, "카페 10% 할인 월 최대 1만원 월 최대 2만원"),
        new RawBenefitStructuringCandidate("holiday", "offer4", "카페", null, "카페 10% 할인 주말 및 공휴일"),
        new RawBenefitStructuringCandidate("ambiguous", "offer5", "기타", null, "최대 10% 할인")));
    when(persistence.persist(any(), any())).thenAnswer(invocation -> {
      StructuredBenefitWrite write = invocation.getArgument(0);
      return !"point".equals(write.benefitId());
    });

    int persisted = new BenefitStructuringBatchService(mapper, persistence).persistReadyCandidates();

    assertEquals(7, persisted);
    ArgumentCaptor<StructuredBenefitWrite> writes = ArgumentCaptor.forClass(StructuredBenefitWrite.class);
    verify(persistence, Mockito.times(8)).persist(writes.capture(), any());
    StructuredBenefitWrite monthlyLimitWrite = writes.getAllValues().stream()
        .filter(write -> "amount-limit".equals(write.benefitId()))
        .findFirst()
        .orElseThrow();
    assertEquals("10000", monthlyLimitWrite.monthlyRewardLimit().toPlainString());
    StructuredBenefitWrite fixed = find(writes, "fixed");
    StructuredBenefitWrite cashback = find(writes, "cashback");
    StructuredBenefitWrite point = find(writes, "point");
    StructuredBenefitWrite mile = find(writes, "mile");
    assertEquals("fixed_amount", fixed.valueType());
    assertEquals("KRW", fixed.rewardUnit());
    assertEquals("cashback", cashback.rewardType());
    assertEquals("point", point.rewardUnit());
    assertEquals("mile", mile.rewardUnit());
    verify(mapper).markPartial("daily-limit", "자동 구조화 보류: 일·연 금액 한도 계산 모델 필요");
    verify(mapper).markPartial("multiple-monthly", "자동 구조화 보류: 복수 월 금액 한도 해석 필요");
    verify(mapper).markPartial("holiday", "자동 구조화 보류: 공휴일 정본 데이터 필요");
  }

  private StructuredBenefitWrite find(
      ArgumentCaptor<StructuredBenefitWrite> writes, String benefitId) {
    return writes.getAllValues().stream()
        .filter(write -> benefitId.equals(write.benefitId()))
        .findFirst()
        .orElseThrow();
  }
}
