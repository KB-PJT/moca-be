package com.moca.mocabe.domain.benefit.structuring;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 평일·주말·요일·HH:mm~HH:mm 조건을 파싱한다. 공휴일 예외는 여기서 확정하지 않는다. */
public class ScheduleParser {
  private static final Pattern TIME = Pattern.compile(
      "([0-2]?[0-9]):([0-5][0-9])\\s*[~∼-]\\s*([0-2]?[0-9]):([0-5][0-9])");
  private final BenefitTextNormalizer normalizer = new BenefitTextNormalizer();

  public Optional<ParsedSchedule> parse(String detailText, String summary, String title) {
    String text = normalizer.normalize(detailText, summary, title);
    Set<DayOfWeek> days = days(text);
    Matcher time = TIME.matcher(text);
    LocalTime start = null;
    LocalTime end = null;
    if (time.find()) {
      start = LocalTime.of(Integer.parseInt(time.group(1)), Integer.parseInt(time.group(2)));
      end = LocalTime.of(Integer.parseInt(time.group(3)), Integer.parseInt(time.group(4)));
    }
    return days.isEmpty() && start == null ? Optional.empty() : Optional.of(new ParsedSchedule(days, start, end));
  }

  private Set<DayOfWeek> days(String text) {
    if (text.contains("평일") || text.contains("월~금") || text.contains("월~ 금")) {
      return EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }
    if (text.contains("주말")) {
      return EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }
    if (text.contains("토요일")) {
      return Set.of(DayOfWeek.SATURDAY);
    }
    if (text.contains("금요일")) {
      return Set.of(DayOfWeek.FRIDAY);
    }
    return Set.of();
  }
}
