package com.moca.mocabe.domain.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.dto.HomeGreetingResponse;
import com.moca.mocabe.domain.home.dto.RecentBenefitsResponse;
import com.moca.mocabe.domain.home.mapper.HomeMapper;
import com.moca.mocabe.domain.home.model.HomeCardRow;
import com.moca.mocabe.domain.home.model.RecentBenefitRow;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.exception.home.InvalidHomeQueryException;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeQueryServiceTest {

  private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

  @Mock private UserMapper userMapper;

  @Mock private HomeMapper homeMapper;

  private HomeQueryService homeQueryService;

  @BeforeEach
  void setUp() {
    homeQueryService = new HomeQueryService(userMapper, homeMapper);
  }

  @Test
  @DisplayName("yearMonth을 생략하면 서울 기준 현재 월을 사용해 인사 정보를 반환한다")
  void usesCurrentSeoulMonthWhenYearMonthIsMissing() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));

    HomeGreetingResponse response = homeQueryService.getGreeting(USER_ID, null);

    assertEquals("지민", response.getNickname());
    assertEquals(YearMonth.now(ZoneId.of("Asia/Seoul")).toString(), response.getYearMonth());
    assertEquals(0, response.getMissedBenefitAmount());
  }

  @Test
  @DisplayName("놓친 혜택이 있으면 금액을 포함한 인사 문구를 반환한다")
  void includesMissedBenefitAmountInGreeting() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));
    when(homeMapper.sumMissedBenefitAmount(USER_ID, "2026-07")).thenReturn(8_200L);

    HomeGreetingResponse response = homeQueryService.getGreeting(USER_ID, "2026-07");

    assertEquals(8_200L, response.getMissedBenefitAmount());
    assertEquals("이번 달 혜택 8,200원을 놓치고 있어요!", response.getMessage());
  }

  @Test
  @DisplayName("카드 조회는 저장된 정렬 모드와 카드 배열의 선택 카드를 반환한다")
  void returnsCardsWithSavedOrderMode() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "MANUAL"));
    HomeCardRow row = card("01980d6a-5c0c-7aaf-9b85-010203040531", "신한 Mr.Life", 1, 0, 0, 0, 0);
    when(homeMapper.findHomeCards(USER_ID, "2026-07")).thenReturn(List.of(row));

    HomeCardsResponse response = homeQueryService.getCards(USER_ID, "2026-07", null);

    assertEquals("2026-07", response.getYearMonth());
    assertEquals("MANUAL", response.getOrderMode());
    assertEquals(row.getUserCardId(), response.getSelectedUserCardId());
    assertEquals("신한 Mr.Life", response.getCards().get(0).getCardName());
  }

  @Test
  @DisplayName("자동 정렬을 명시하면 첫 카드에 자동 정렬 안내를 표시한다")
  void returnsCardsWithRequestedAutoOrderMode() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "MANUAL"));
    HomeCardRow row = card("01980d6a-5c0c-7aaf-9b85-010203040532", "신한 Deep Dream", 1, 0, 0, 0, 0);
    when(homeMapper.findHomeCards(USER_ID, "2026-07")).thenReturn(List.of(row));

    HomeCardsResponse response = homeQueryService.getCards(USER_ID, "2026-07", "auto");

    assertEquals("AUTO", response.getOrderMode());
    assertEquals("다음 실적 구간까지 남은 금액이 가장 적은 카드", response.getCards().get(0).getAutoOrderReason());
  }

  @Test
  @DisplayName("보유 카드가 없으면 빈 캐러셀을 반환한다")
  void returnsEmptyCardsWhenNoCardsExist() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));
    when(homeMapper.findHomeCards(USER_ID, "2026-07")).thenReturn(List.of());

    HomeCardsResponse response = homeQueryService.getCards(USER_ID, "2026-07", null);

    assertNull(response.getSelectedUserCardId());
    assertEquals(List.of(), response.getCards());
  }

  @Test
  @DisplayName("저장된 카드 정렬 방식이 없으면 자동 정렬을 기본값으로 사용한다")
  void defaultsToAutoOrderWhenSavedModeIsMissing() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", null));
    when(homeMapper.findHomeCards(USER_ID, "2026-07")).thenReturn(List.of());

    assertEquals("AUTO", homeQueryService.getCards(USER_ID, "2026-07", null).getOrderMode());
  }

  @Test
  @DisplayName("최근 혜택 내역이 없으면 빈 배열을 반환한다")
  void returnsEmptyRecentBenefits() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));
    when(homeMapper.findRecentBenefits(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(5)))
        .thenReturn(List.of());

    assertEquals(
        List.of(), homeQueryService.getRecentBenefits(USER_ID, "2026-07", 5).getBenefits());
  }

  @Test
  @DisplayName("최근 혜택 조회 결과가 null이면 빈 배열로 처리한다")
  void treatsNullRecentBenefitsAsEmpty() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));
    when(homeMapper.findRecentBenefits(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(5)))
        .thenReturn(null);

    assertEquals(
        List.of(), homeQueryService.getRecentBenefits(USER_ID, "2026-07", 5).getBenefits());
  }

  @Test
  @DisplayName("존재하지 않는 사용자의 홈 조회는 사용자 없음 오류로 거절한다")
  void rejectsUnknownUser() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(null);

    assertThrows(
        UserNotFoundException.class, () -> homeQueryService.getGreeting(USER_ID, "2026-07"));
  }

  @Test
  @DisplayName("잘못된 월·정렬 모드·내역 수는 400용 예외로 거절한다")
  void rejectsInvalidHomeQuery() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));

    assertThrows(
        InvalidHomeQueryException.class, () -> homeQueryService.getGreeting(USER_ID, "2026-13"));
    assertThrows(
        InvalidHomeQueryException.class,
        () -> homeQueryService.getCards(USER_ID, "2026-07", "RANDOM"));
    assertThrows(
        InvalidHomeQueryException.class,
        () -> homeQueryService.getRecentBenefits(USER_ID, "2026-07", 6));
  }

  @Test
  @DisplayName("카드 요약과 최근 혜택을 실제 집계값으로 변환한다")
  void mapsHomeAggregates() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));
    HomeCardRow row = card("card-1", "신한 Mr.Life", 1, 30000, 21800, 382000, 500000);
    row.setHighlightBenefitTitle("스타벅스 10% 할인");
    when(homeMapper.findHomeCards(USER_ID, "2026-07")).thenReturn(List.of(row));
    RecentBenefitRow recentBenefit = new RecentBenefitRow();
    recentBenefit.setBenefitHistoryId("usage-1");
    recentBenefit.setMerchantName("스타벅스");
    recentBenefit.setBenefitType("DISCOUNT");
    recentBenefit.setBenefitTitle("카페 10% 할인");
    recentBenefit.setCardName("신한 Mr.Life");
    recentBenefit.setPaymentAmount(15000);
    recentBenefit.setBenefitAmount(1500);
    recentBenefit.setOccurredAt(LocalDateTime.of(2026, 7, 27, 5, 30));
    when(homeMapper.findRecentBenefits(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(5)))
        .thenReturn(List.of(recentBenefit));

    HomeCardsResponse cards = homeQueryService.getCards(USER_ID, "2026-07", "AUTO");
    RecentBenefitsResponse benefits = homeQueryService.getRecentBenefits(USER_ID, "2026-07", 5);

    assertEquals(8200, cards.getCards().get(0).getSummary().getAvailableBenefitAmount());
    assertEquals(76, cards.getCards().get(0).getSummary().getPerformanceRate());
    assertEquals(
        "월 최대 30,000원", cards.getCards().get(0).getHighlightBenefit().getMonthlyLimitText());
    assertEquals("2026-07-27T14:30:00+09:00", benefits.getBenefits().get(0).getOccurredAt());
  }

  @Test
  @DisplayName("받은 혜택이 계산 가능한 월 한도보다 크면 잔여 혜택을 0으로 표시한다")
  void clampsAvailableBenefitAmountToZero() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "MANUAL"));
    HomeCardRow row = card("card-1", "한도 미확인 카드", 1, 0, 7_640, 0, 0);
    when(homeMapper.findHomeCards(USER_ID, "2026-07")).thenReturn(List.of(row));

    HomeCardsResponse response = homeQueryService.getCards(USER_ID, "2026-07", null);

    assertEquals(0, response.getCards().get(0).getSummary().getMaximumMonthlyBenefitAmount());
    assertEquals(7_640, response.getCards().get(0).getSummary().getReceivedBenefitAmount());
    assertEquals(0, response.getCards().get(0).getSummary().getAvailableBenefitAmount());
  }

  @Test
  @DisplayName("실적 목표가 없는 카드는 자동 정렬 뒤로 보내고 달성률을 0으로 표시한다")
  void sortsCardsWithoutPerformanceTargetLast() {
    when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));
    HomeCardRow withoutTarget = card("no-target", "목표 없음", 1, 0, 0, 0, 0);
    HomeCardRow withTarget = card("target", "목표 있음", 2, 0, 0, 1, 100);
    when(homeMapper.findHomeCards(USER_ID, "2026-07"))
        .thenReturn(List.of(withoutTarget, withTarget));

    HomeCardsResponse response = homeQueryService.getCards(USER_ID, "2026-07", "AUTO");

    assertEquals("target", response.getCards().get(0).getUserCardId());
    assertEquals(0, response.getCards().get(1).getSummary().getPerformanceRate());
  }

  private HomeCardRow card(
      String userCardId,
      String cardName,
      int displayOrder,
      long maximumBenefit,
      long receivedBenefit,
      long currentSpend,
      long targetSpend) {
    HomeCardRow row = new HomeCardRow();
    row.setUserCardId(userCardId);
    row.setCardName(cardName);
    row.setDisplayOrder(displayOrder);
    row.setMaximumMonthlyBenefitAmount(maximumBenefit);
    row.setReceivedBenefitAmount(receivedBenefit);
    row.setPerformanceCurrentAmount(currentSpend);
    row.setPerformanceTargetAmount(targetSpend);
    return row;
  }

  private UserProfile profile(String nickname, String cardSortMode) {
    UserProfile profile = new UserProfile();
    profile.setUserId(USER_ID);
    profile.setNickname(nickname);
    profile.setCardSortMode(cardSortMode);
    return profile;
  }
}
