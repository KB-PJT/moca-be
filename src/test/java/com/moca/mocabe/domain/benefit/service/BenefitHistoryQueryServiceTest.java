package com.moca.mocabe.domain.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.dto.BenefitHistoryDetailResponse;
import com.moca.mocabe.domain.benefit.dto.BenefitHistoryResponse;
import com.moca.mocabe.domain.benefit.mapper.BenefitHistoryMapper;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryDetailRow;
import com.moca.mocabe.domain.benefit.model.BenefitHistoryRow;
import com.moca.mocabe.global.exception.benefit.BenefitHistoryNotFoundException;
import com.moca.mocabe.global.exception.benefit.InvalidBenefitHistoryQueryException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BenefitHistoryQueryServiceTest {
    @Mock private BenefitHistoryMapper mapper;
    @Test void filtersAndPaginatesOnlyTheAuthenticatedUsersHistory() {
        when(mapper.countHistory(eq("user-1"), any(), any(), eq("card-1"), eq("DISCOUNT"))).thenReturn(21L);
        when(mapper.findHistory(eq("user-1"), any(), any(), eq("card-1"), eq("DISCOUNT"), eq("BENEFIT_DESC"), eq(20), eq(20))).thenReturn(List.of(row()));
        BenefitHistoryResponse result = new BenefitHistoryQueryService(mapper).getHistory("user-1", "2026-07", "card-1", "discount", "benefit_desc", 2, 20);
        assertEquals(21, result.getMeta().getTotalCount()); assertEquals(false, result.getMeta().isHasNext());
        assertEquals("2026-07-17T14:30:00+09:00", result.getData().get(0).getApprovedAt());
    }
    @Test void rejectsInvalidFiltersAndPageBounds() {
        BenefitHistoryQueryService service = new BenefitHistoryQueryService(mapper);
        assertThrows(InvalidBenefitHistoryQueryException.class, () -> service.getHistory("u", "2026-13", null, null, null, 1, 20));
        assertThrows(InvalidBenefitHistoryQueryException.class, () -> service.getHistory("u", "2026-07", null, "MILEAGE", null, 1, 20));
        assertThrows(InvalidBenefitHistoryQueryException.class, () -> service.getHistory("u", "2026-07", null, null, "OLD", 0, 20));
    }
    @Test void returnsDetailWithMonthlyLimitAndOriginalMileage() {
        BenefitHistoryDetailRow row = new BenefitHistoryDetailRow(); copy(row); row.setMonthlyUsedAmount(1100); row.setMonthlyLimitAmount(5000); row.setEarnedMileage(1200L);
        when(mapper.findDetail("user-1", "usage-1")).thenReturn(row);
        BenefitHistoryDetailResponse result = new BenefitHistoryQueryService(mapper).getDetail("user-1", "usage-1");
        assertEquals(3900, result.getMonthlyLimit().getRemainingAmount()); assertEquals(1200L, result.getEarnedMileage());
    }
    @Test void hidesAnotherUsersDetailAsNotFound() { when(mapper.findDetail("user-1", "usage-2")).thenReturn(null); assertThrows(BenefitHistoryNotFoundException.class, () -> new BenefitHistoryQueryService(mapper).getDetail("user-1", "usage-2")); }
    private BenefitHistoryRow row() { BenefitHistoryRow row=new BenefitHistoryRow(); copy(row); return row; }
    private void copy(BenefitHistoryRow row) { row.setBenefitHistoryId("usage-1");row.setMerchantName("스타벅스");row.setApprovedAt(LocalDateTime.of(2026,7,17,5,30));row.setPaymentAmount(15000);row.setBenefitAmount(1500);row.setBenefitType("DISCOUNT");row.setBenefitTitle("카페 할인");row.setUserCardId("card-1");row.setCardName("카드");row.setCalculationStatus("APPLIED"); }
}
