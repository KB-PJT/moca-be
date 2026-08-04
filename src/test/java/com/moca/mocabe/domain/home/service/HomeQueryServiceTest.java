package com.moca.mocabe.domain.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.dto.HomeGreetingResponse;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.exception.home.InvalidHomeQueryException;
import com.moca.mocabe.global.exception.home.HomeDataNotFoundException;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.util.List;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeQueryServiceTest {

    private static final String USER_ID = "01980d6a-5c0c-7aaf-9b85-010203040506";

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserCardMapper userCardMapper;

    private HomeQueryService homeQueryService;

    @BeforeEach
    void setUp() {
        homeQueryService = new HomeQueryService(userMapper, userCardMapper);
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
    @DisplayName("카드 조회는 저장된 정렬 모드와 카드 배열의 선택 카드를 반환한다")
    void returnsCardsWithSavedOrderMode() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "MANUAL"));
        UserCardListRow row = new UserCardListRow();
        row.setUserCardId("01980d6a-5c0c-7aaf-9b85-010203040531");
        row.setCardName("신한 Mr.Life");
        when(userCardMapper.findHomeCardsByUserId(USER_ID, "MANUAL")).thenReturn(List.of(row));

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
        UserCardListRow row = new UserCardListRow();
        row.setUserCardId("01980d6a-5c0c-7aaf-9b85-010203040532");
        row.setCardName("신한 Deep Dream");
        when(userCardMapper.findHomeCardsByUserId(USER_ID, "AUTO")).thenReturn(List.of(row));

        HomeCardsResponse response = homeQueryService.getCards(USER_ID, "2026-07", "auto");

        assertEquals("AUTO", response.getOrderMode());
        assertEquals("사용자가 저장한 자동 정렬 순서", response.getCards().get(0).getAutoOrderReason());
    }

    @Test
    @DisplayName("보유 카드가 없으면 404용 예외를 반환한다")
    void rejectsWhenCardsAreEmpty() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));
        when(userCardMapper.findHomeCardsByUserId(USER_ID, "AUTO")).thenReturn(List.of());

        assertThrows(HomeDataNotFoundException.class,
                () -> homeQueryService.getCards(USER_ID, "2026-07", null));
    }

    @Test
    @DisplayName("저장된 카드 정렬 방식이 없으면 자동 정렬을 기본값으로 사용한다")
    void defaultsToAutoOrderWhenSavedModeIsMissing() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", null));
        when(userCardMapper.findHomeCardsByUserId(USER_ID, "AUTO")).thenReturn(List.of());

        assertThrows(HomeDataNotFoundException.class,
                () -> homeQueryService.getCards(USER_ID, "2026-07", null));
    }

    @Test
    @DisplayName("최근 혜택 내역이 없으면 404용 예외를 반환한다")
    void rejectsWhenRecentBenefitsAreEmpty() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));

        assertThrows(HomeDataNotFoundException.class,
                () -> homeQueryService.getRecentBenefits(USER_ID, "2026-07", 5));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 홈 조회는 사용자 없음 오류로 거절한다")
    void rejectsUnknownUser() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(null);

        assertThrows(UserNotFoundException.class, () -> homeQueryService.getGreeting(USER_ID, "2026-07"));
    }

    @Test
    @DisplayName("잘못된 월·정렬 모드·내역 수는 400용 예외로 거절한다")
    void rejectsInvalidHomeQuery() {
        when(userMapper.findProfileById(USER_ID)).thenReturn(profile("지민", "AUTO"));

        assertThrows(InvalidHomeQueryException.class,
                () -> homeQueryService.getGreeting(USER_ID, "2026-13"));
        assertThrows(InvalidHomeQueryException.class,
                () -> homeQueryService.getCards(USER_ID, "2026-07", "RANDOM"));
        assertThrows(InvalidHomeQueryException.class,
                () -> homeQueryService.getRecentBenefits(USER_ID, "2026-07", 6));
    }

    private UserProfile profile(String nickname, String cardSortMode) {
        UserProfile profile = new UserProfile();
        profile.setUserId(USER_ID);
        profile.setNickname(nickname);
        profile.setCardSortMode(cardSortMode);
        return profile;
    }
}
