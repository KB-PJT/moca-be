package com.moca.mocabe.domain.benefit.structuring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("혜택 일정 공통 파서")
class ScheduleParserTest {
  private final ScheduleParser parser = new ScheduleParser();

  @Test
  @DisplayName("평일 점심 시간 조건을 KST 일정으로 분리한다")
  void parsesWeekdayLunchSchedule() {
    ParsedSchedule schedule = parser.parse("평일 11:00~14:00 결제 시 할인", null, null).orElseThrow();

    assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), schedule.days());
    assertEquals(LocalTime.of(11, 0), schedule.startTime());
    assertEquals(LocalTime.of(14, 0), schedule.endTime());
  }

  @Test
  @DisplayName("주말·단일 요일·시간 없는 문구를 각각 구분한다")
  void parsesWeekendAndSingleDays() {
    assertEquals(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        parser.parse("주말 할인", null, null).orElseThrow().days());
    assertEquals(Set.of(DayOfWeek.SATURDAY),
        parser.parse("토요일 할인", null, null).orElseThrow().days());
    assertEquals(Set.of(DayOfWeek.FRIDAY),
        parser.parse("금요일 할인", null, null).orElseThrow().days());
    assertEquals(true, parser.parse("요일 조건 없음", null, null).isEmpty());
  }
}
