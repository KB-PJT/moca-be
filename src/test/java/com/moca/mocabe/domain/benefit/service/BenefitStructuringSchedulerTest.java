package com.moca.mocabe.domain.benefit.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("혜택 구조화 운영 배치")
class BenefitStructuringSchedulerTest {
  @Test
  @DisplayName("기본 비활성 상태에서는 데이터를 변경하지 않는다")
  void skipsWhenDisabled() {
    BenefitStructuringBatchService service = Mockito.mock(BenefitStructuringBatchService.class);

    new BenefitStructuringScheduler(service, false).structurePendingBenefits();

    verify(service, never()).persistReadyCandidates();
  }

  @Test
  @DisplayName("운영 스위치를 활성화하면 안전 후보를 구조화한다")
  void persistsWhenEnabled() {
    BenefitStructuringBatchService service = Mockito.mock(BenefitStructuringBatchService.class);
    when(service.persistReadyCandidates()).thenReturn(3);

    new BenefitStructuringScheduler(service, true).structurePendingBenefits();

    verify(service).persistReadyCandidates();
  }
}
