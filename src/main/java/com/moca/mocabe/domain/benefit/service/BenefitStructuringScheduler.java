package com.moca.mocabe.domain.benefit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** 운영에서 명시적으로 활성화한 경우에만 미구조화 카드 혜택을 야간에 안전 구조화한다. */
public class BenefitStructuringScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(BenefitStructuringScheduler.class);

  private final BenefitStructuringBatchService batchService;
  private final boolean enabled;

  public BenefitStructuringScheduler(BenefitStructuringBatchService batchService, boolean enabled) {
    this.batchService = batchService;
    this.enabled = enabled;
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
  public void structurePendingBenefits() {
    if (!enabled) {
      return;
    }
    int persisted = batchService.persistReadyCandidates();
    LOGGER.info("카드 혜택 자동 구조화 완료 persisted={}", persisted);
  }
}
