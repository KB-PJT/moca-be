package com.moca.mocabe.domain.benefit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.dto.BenefitHistoryDetailResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryMetaResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryResponse;
import com.moca.mocabe.domain.benefit.dto.MonthlyLimitResponse;
import com.moca.mocabe.domain.benefit.service.BenefitHistoryQueryService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenefitHistoryControllerTest {

  @Test
  void delegatesHistoryAndDetailQueriesUsingAuthenticatedUser() {
    BenefitHistoryQueryService service = org.mockito.Mockito.mock(BenefitHistoryQueryService.class);
    CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
    when(currentUser.getCurrentUserId()).thenReturn("user-1");
    BenefitHistoryResponse history =
        new BenefitHistoryResponse(List.of(), new BenefitHistoryMetaResponse(1, 20, 0, false));
    BenefitHistoryDetailResponse detail =
        new BenefitHistoryDetailResponse(
            "history-1",
            "APPLIED",
            "스타벅스",
            "2026-07-01T10:00:00+09:00",
            "카드",
            1,
            1,
            "DISCOUNT",
            "할인",
            new MonthlyLimitResponse(1, 1, 0),
            null);
    when(service.getHistory("user-1", "2026-07", "card-1", "DISCOUNT", "LATEST", 1, 20))
        .thenReturn(history);
    when(service.getDetail("user-1", "history-1")).thenReturn(detail);
    BenefitHistoryController controller = new BenefitHistoryController(service, currentUser);

    assertEquals(
        history,
        controller
            .getHistory("2026-07", "card-1", "DISCOUNT", "LATEST", 1, 20)
            .getBody()
            .getData());
    assertEquals(detail, controller.getDetail("history-1").getBody().getData());
    verify(service).getHistory("user-1", "2026-07", "card-1", "DISCOUNT", "LATEST", 1, 20);
    verify(service).getDetail("user-1", "history-1");
  }
}
