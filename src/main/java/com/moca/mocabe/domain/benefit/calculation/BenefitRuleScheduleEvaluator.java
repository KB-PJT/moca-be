package com.moca.mocabe.domain.benefit.calculation;

import com.moca.mocabe.domain.benefit.model.BenefitRuleSchedule;
import java.time.LocalDateTime;
import java.util.Set;

/** 한 규칙의 일정 행들을 OR로 평가한다. */
public class BenefitRuleScheduleEvaluator {

  public boolean matches(Set<BenefitRuleSchedule> schedules, LocalDateTime approvedAt) {
    return schedules == null
        || schedules.isEmpty()
        || schedules.stream().anyMatch(schedule -> schedule.matches(approvedAt));
  }
}
