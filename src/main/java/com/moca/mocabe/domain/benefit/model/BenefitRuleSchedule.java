package com.moca.mocabe.domain.benefit.model;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * Asia/Seoul 기준으로 평가하는 혜택 일정 조건이다.
 * 각 차원의 조건은 AND이며, 한 규칙에 일정이 여러 개면 일정끼리는 OR로 평가한다.
 */
public record BenefitRuleSchedule(
        Set<Integer> months,
        Set<Integer> daysOfMonth,
        Set<DayOfWeek> daysOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {

    public BenefitRuleSchedule {
        months = months == null ? Set.of() : Set.copyOf(months);
        daysOfMonth = daysOfMonth == null ? Set.of() : Set.copyOf(daysOfMonth);
        daysOfWeek = daysOfWeek == null ? Set.of() : Set.copyOf(daysOfWeek);
        validateRange(months, 1, 12, "months");
        validateRange(daysOfMonth, 1, 31, "daysOfMonth");
        if ((startTime == null) != (endTime == null)) {
            throw new IllegalArgumentException("startTime과 endTime은 함께 설정해야 합니다.");
        }
        if (startTime != null && startTime.equals(endTime)) {
            throw new IllegalArgumentException("같은 시작·종료 시각은 허용하지 않습니다.");
        }
    }

    public boolean matches(LocalDateTime approvedAt) {
        if (approvedAt == null) {
            return false;
        }
        if (!months.isEmpty() && !months.contains(approvedAt.getMonthValue())) {
            return false;
        }
        if (!daysOfMonth.isEmpty() && !daysOfMonth.contains(approvedAt.getDayOfMonth())) {
            return false;
        }
        if (!daysOfWeek.isEmpty() && !daysOfWeek.contains(approvedAt.getDayOfWeek())) {
            return false;
        }
        return matchesTime(approvedAt.toLocalTime());
    }

    private boolean matchesTime(LocalTime approvedTime) {
        if (startTime == null) {
            return true;
        }
        if (startTime.isBefore(endTime)) {
            return !approvedTime.isBefore(startTime) && approvedTime.isBefore(endTime);
        }
        return !approvedTime.isBefore(startTime) || approvedTime.isBefore(endTime);
    }

    private static void validateRange(Set<Integer> values, int minimum, int maximum, String fieldName) {
        if (values.stream().anyMatch(value -> value == null || value < minimum || value > maximum)) {
            throw new IllegalArgumentException(fieldName + " 값의 범위가 올바르지 않습니다.");
        }
    }
}
