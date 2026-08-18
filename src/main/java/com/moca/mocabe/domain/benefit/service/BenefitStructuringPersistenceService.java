package com.moca.mocabe.domain.benefit.service;

import com.moca.mocabe.domain.benefit.mapper.BenefitStructuringMapper;
import com.moca.mocabe.domain.benefit.model.StructuredBenefitWrite;
import com.moca.mocabe.domain.benefit.structuring.ParsedTarget;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** target까지 확정된 공통 parser 결과를 원자적으로 반영한다. */
@Service
public class BenefitStructuringPersistenceService {
  private final BenefitStructuringMapper mapper;

  public BenefitStructuringPersistenceService(BenefitStructuringMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional
  public boolean persist(StructuredBenefitWrite write, ParsedTarget target) {
    if (mapper.updateOffer(write) != 1) {
      return false;
    }
    int inserted = mapper.insertRule(write);
    if (inserted == 0) {
      return false;
    }
    int targetInserted;
    targetInserted = switch (target.type()) {
      case ALL_MERCHANTS -> mapper.insertAllMerchantsTarget(write);
      case MERCHANT -> mapper.insertMerchantTarget(write);
      case MERCHANT_CATEGORY -> mapper.insertCategoryTarget(write);
    };
    if (targetInserted != 1) {
      throw new IllegalStateException("구조화 혜택 대상 저장에 실패했습니다.");
    }
    persistMonthlyRewardLimit(write);
    mapper.markStructured(write);
    return true;
  }

  private void persistMonthlyRewardLimit(StructuredBenefitWrite write) {
    if (write.monthlyRewardLimit() == null) {
      return;
    }
    if (mapper.insertMonthlyRewardLimitPolicy(write) != 1
        || mapper.insertMonthlyRewardLimitTier(write) != 1) {
      throw new IllegalStateException("구조화 혜택 월 한도 저장에 실패했습니다.");
    }
  }
}
