package com.moca.mocabe.domain.benefit.structuring;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/** 서버 시각으로 판정 가능한 요일·시간 조건이다. */
public record ParsedSchedule(Set<DayOfWeek> days, LocalTime startTime, LocalTime endTime) {
  public ParsedSchedule {
    days = days == null ? Set.of() : Set.copyOf(days);
  }
}
