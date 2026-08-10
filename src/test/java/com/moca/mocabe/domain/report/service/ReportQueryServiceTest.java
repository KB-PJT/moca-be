package com.moca.mocabe.domain.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.codef.exception.UserCardNotFoundException;
import com.moca.mocabe.domain.report.dto.BenefitCategoriesReportResponse;
import com.moca.mocabe.domain.report.dto.BenefitSummaryReportResponse;
import com.moca.mocabe.domain.report.dto.MissedBenefitsReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceCardsReportResponse;
import com.moca.mocabe.domain.report.dto.PerformanceSummaryReportResponse;
import com.moca.mocabe.domain.report.mapper.ReportMapper;
import com.moca.mocabe.domain.report.model.BenefitTypeAmountRow;
import com.moca.mocabe.domain.report.model.CategoryBenefitRow;
import com.moca.mocabe.domain.report.model.MissedBenefitRow;
import com.moca.mocabe.domain.report.model.PerformanceCardRow;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.exception.report.InvalidReportQueryException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

  private static final String USER_ID = "user-1";
  private static final String CARD_ID = "card-1";

  @Mock private UserMapper userMapper;
  @Mock private ReportMapper reportMapper;
  private ReportQueryService service;

  @BeforeEach
  void setUp() {
    service = new ReportQueryService(userMapper, reportMapper);
    UserProfile profile = new UserProfile();
    profile.setUserId(USER_ID);
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile);
  }

  @Test
  void aggregatesCurrentAndPreviousMonthBenefits() {
    when(reportMapper.findBenefitAmountsByType(eq(USER_ID), any(), any()))
        .thenReturn(
            List.of(
                new BenefitTypeAmountRow("DISCOUNT", 21_000),
                new BenefitTypeAmountRow("POINT", 8_700)))
        .thenReturn(List.of(new BenefitTypeAmountRow("DISCOUNT", 31_800)));

    BenefitSummaryReportResponse response = service.getBenefitSummary(USER_ID, "2026-07");

    assertEquals(29_700, response.totalBenefitAmount());
    assertEquals(31_800, response.previousMonthBenefitAmount());
    assertEquals(-2_100, response.differenceAmount());
    assertEquals("할인", response.breakdown().get(0).label());
  }

  @Test
  void exposesUnusedMonthlyLimitAsMissedBenefit() {
    PerformanceCardRow card = new PerformanceCardRow(CARD_ID, "카드", null, 300_000, 500_000, 1);
    when(reportMapper.findPerformanceCard(USER_ID, CARD_ID, "2026-07")).thenReturn(card);
    when(reportMapper.findMonthlyRemainingBenefits(USER_ID, CARD_ID, "2026-07"))
        .thenReturn(List.of(new MissedBenefitRow("rule-1", "카페 할인", "DISCOUNT", 3_000, 5_000)));

    MissedBenefitsReportResponse response = service.getMissedBenefits(USER_ID, "2026-07", CARD_ID);

    assertEquals(2_000, response.totalMissedBenefitAmount());
    assertEquals(2_000, response.benefits().get(0).remainingAmount());
    assertEquals("KRW", response.benefits().get(0).unit());
  }

  @Test
  void numbersBenefitCategoriesAndExcludesExhaustedLimits() {
    when(reportMapper.findBenefitAmountsByCategory(eq(USER_ID), any(), any(), eq(3)))
        .thenReturn(
            List.of(
                new CategoryBenefitRow("CAFE", "카페", 1_000),
                new CategoryBenefitRow("TRANSPORT", "교통", 500)));
    PerformanceCardRow card = new PerformanceCardRow(CARD_ID, "카드", null, 0, 0, 0);
    when(reportMapper.findPerformanceCard(USER_ID, CARD_ID, "2026-07")).thenReturn(card);
    when(reportMapper.findMonthlyRemainingBenefits(USER_ID, CARD_ID, "2026-07"))
        .thenReturn(List.of(new MissedBenefitRow("rule", "소진", "DISCOUNT", 1_000, 1_000)));

    BenefitCategoriesReportResponse categories =
        service.getBenefitCategories(USER_ID, "2026-07", 3);
    MissedBenefitsReportResponse missed = service.getMissedBenefits(USER_ID, "2026-07", CARD_ID);

    assertEquals(1, categories.categories().get(0).rank());
    assertEquals(2, categories.categories().get(1).rank());
    assertEquals(0, missed.totalMissedBenefitAmount());
    assertEquals(List.of(), missed.benefits());
  }

  @Test
  void rejectsUnknownUserAndMissingMissedCardParameter() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(null);

    assertThrows(
        com.moca.mocabe.global.exception.user.UserNotFoundException.class,
        () -> service.getBenefitSummary(USER_ID, "2026-07"));
    when(userMapper.findProfileById(USER_ID)).thenReturn(new UserProfile());
    assertThrows(
        InvalidReportQueryException.class,
        () -> service.getMissedBenefits(USER_ID, "2026-07", " "));
  }

  @Test
  void usesDefaultMonthAndKeepsUnknownBenefitTypeLabel() {
    when(reportMapper.findBenefitAmountsByType(eq(USER_ID), any(), any()))
        .thenReturn(List.of(new BenefitTypeAmountRow("ETC", 1)));

    BenefitSummaryReportResponse response = service.getBenefitSummary(USER_ID, null);

    assertEquals("ETC", response.breakdown().get(0).label());
  }

  @Test
  void labelsCashbackBenefitType() {
    when(reportMapper.findBenefitAmountsByType(eq(USER_ID), any(), any()))
        .thenReturn(List.of(new BenefitTypeAmountRow("CASHBACK", 1)));

    assertEquals("캐시백", service.getBenefitSummary(USER_ID, "2026-07").breakdown().get(0).label());
  }

  @Test
  void rejectsCardOutsideCurrentUsersOwnership() {
    when(reportMapper.findPerformanceCard(USER_ID, CARD_ID, "2026-07")).thenReturn(null);

    assertThrows(
        UserCardNotFoundException.class,
        () -> service.getMissedBenefits(USER_ID, "2026-07", CARD_ID));
  }

  @Test
  void returnsOnlyThreeCardsInPerformanceSummaryButCountsAllCards() {
    when(reportMapper.findPerformanceCards(USER_ID, "2026-07"))
        .thenReturn(
            List.of(
                new PerformanceCardRow("1", "A", null, 100, 100, 1),
                new PerformanceCardRow("2", "B", null, 50, 100, 2),
                new PerformanceCardRow("3", "C", null, 0, 0, 3),
                new PerformanceCardRow("4", "D", null, 10, 100, 4)));

    PerformanceSummaryReportResponse summary = service.getPerformanceSummary(USER_ID, "2026-07");
    PerformanceCardsReportResponse cards = service.getPerformanceCards(USER_ID, "2026-07");

    assertEquals(4, summary.cardCount());
    assertEquals(1, summary.achievedCardCount());
    assertEquals(3, summary.cards().size());
    assertEquals(4, cards.cards().size());
    assertEquals(0, cards.cards().get(2).achievementRate());
  }

  @Test
  void exposesCurrentAndNextPerformanceTierFromMapper() {
    when(reportMapper.findPerformanceCards(USER_ID, "2026-07"))
        .thenReturn(
            List.of(new PerformanceCardRow(CARD_ID, "카드", null, 382_000, 500_000, 1, 2, 1)));

    PerformanceCardsReportResponse response = service.getPerformanceCards(USER_ID, "2026-07");

    assertEquals(1, response.cards().get(0).currentTier());
    assertEquals(2, response.cards().get(0).nextTier());
    assertEquals(118_000, response.cards().get(0).remainingAmountToNextTier());
  }

  @Test
  void validatesReportParameters() {
    assertThrows(
        InvalidReportQueryException.class,
        () -> service.getBenefitCategories(USER_ID, "2026-07", 4));
    assertThrows(
        InvalidReportQueryException.class, () -> service.getBenefitSummary(USER_ID, "2026-7"));
  }
}
