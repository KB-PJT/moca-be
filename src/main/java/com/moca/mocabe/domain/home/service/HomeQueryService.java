package com.moca.mocabe.domain.home.service;

import com.moca.mocabe.domain.card.mapper.UserCardMapper;
import com.moca.mocabe.domain.card.model.UserCardListRow;
import com.moca.mocabe.domain.home.dto.HomeBenefitHighlightResponse;
import com.moca.mocabe.domain.home.dto.HomeCardResponse;
import com.moca.mocabe.domain.home.dto.HomeCardSummaryResponse;
import com.moca.mocabe.domain.home.dto.HomeCardsResponse;
import com.moca.mocabe.domain.home.dto.HomeGreetingResponse;
import com.moca.mocabe.domain.home.dto.RecentBenefitsResponse;
import com.moca.mocabe.domain.user.mapper.UserMapper;
import com.moca.mocabe.domain.user.model.UserProfile;
import com.moca.mocabe.global.exception.home.InvalidHomeQueryException;
import com.moca.mocabe.global.exception.home.HomeDataNotFoundException;
import com.moca.mocabe.global.exception.user.UserNotFoundException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

/** 홈 화면 컴포넌트별 조회 유스케이스를 담당한다. */
public class HomeQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private final UserMapper userMapper;
    private final UserCardMapper userCardMapper;

    public HomeQueryService(UserMapper userMapper, UserCardMapper userCardMapper) {
        this.userMapper = userMapper;
        this.userCardMapper = userCardMapper;
    }

    @Transactional(readOnly = true)
    public HomeGreetingResponse getGreeting(String userId, String requestedYearMonth) {
        String yearMonth = normalizeYearMonth(requestedYearMonth);
        UserProfile profile = requireProfile(userId);
        long missedBenefitAmount = 0L;
        String message = "이번 달 놓친 혜택이 없습니다.";
        return new HomeGreetingResponse(profile.getNickname(), yearMonth, missedBenefitAmount, message);
    }

    @Transactional(readOnly = true)
    public HomeCardsResponse getCards(String userId, String requestedYearMonth, String requestedOrderMode) {
        String yearMonth = normalizeYearMonth(requestedYearMonth);
        UserProfile profile = requireProfile(userId);
        String orderMode = normalizeOrderMode(requestedOrderMode, profile.getCardSortMode());
        List<UserCardListRow> rows = userCardMapper.findHomeCardsByUserId(userId, orderMode);
        if (rows == null || rows.isEmpty()) {
            throw new HomeDataNotFoundException("홈 화면에 표시할 보유 카드가 없습니다.");
        }
        List<HomeCardResponse> cards = mapCards(rows, orderMode);
        String selectedUserCardId = cards.get(0).getUserCardId();
        return new HomeCardsResponse(yearMonth, orderMode, selectedUserCardId, cards);
    }

    @Transactional(readOnly = true)
    public RecentBenefitsResponse getRecentBenefits(String userId, String requestedYearMonth, int limit) {
        requireProfile(userId);
        normalizeYearMonth(requestedYearMonth);
        if (limit < 1 || limit > 5) {
            throw new InvalidHomeQueryException("limit은 1에서 5 사이여야 합니다.");
        }
        // 혜택 원천 거래/계산 테이블이 도입되기 전에는 표시할 데이터가 없다.
        throw new HomeDataNotFoundException("최근 혜택 내역이 없습니다.");
    }

    private UserProfile requireProfile(String userId) {
        UserProfile profile = userMapper.findProfileById(userId);
        if (profile == null) {
            throw new UserNotFoundException();
        }
        return profile;
    }

    private List<HomeCardResponse> mapCards(List<UserCardListRow> rows, String orderMode) {
        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> toHomeCard(rows.get(index), index + 1, orderMode))
                .toList();
    }

    private HomeCardResponse toHomeCard(UserCardListRow row, int order, String orderMode) {
        String reason = "AUTO".equals(orderMode) && order == 1
                ? "사용자가 저장한 자동 정렬 순서"
                : null;
        HomeBenefitHighlightResponse highlight = new HomeBenefitHighlightResponse(null, null);
        HomeCardSummaryResponse summary = new HomeCardSummaryResponse(0, 0, 0, 0, 0, 0, 0);
        return new HomeCardResponse(row.getUserCardId(), order, row.getCardName(), row.getMemo(),
                row.getCardImageUrl(), reason, highlight, summary);
    }

    private String normalizeYearMonth(String requestedYearMonth) {
        if (requestedYearMonth == null || requestedYearMonth.isBlank()) {
            return YearMonth.now(SEOUL).format(YEAR_MONTH_FORMATTER);
        }
        try {
            return YearMonth.parse(requestedYearMonth, YEAR_MONTH_FORMATTER).format(YEAR_MONTH_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new InvalidHomeQueryException("yearMonth는 YYYY-MM 형식이어야 합니다.");
        }
    }

    private String normalizeOrderMode(String requestedOrderMode, String savedOrderMode) {
        String orderMode = requestedOrderMode == null || requestedOrderMode.isBlank()
                ? savedOrderMode
                : requestedOrderMode;
        if (orderMode == null || orderMode.isBlank()) {
            return "AUTO";
        }
        String normalized = orderMode.toUpperCase(Locale.ROOT);
        if (!"AUTO".equals(normalized) && !"MANUAL".equals(normalized)) {
            throw new InvalidHomeQueryException("orderMode는 AUTO 또는 MANUAL이어야 합니다.");
        }
        return normalized;
    }
}
