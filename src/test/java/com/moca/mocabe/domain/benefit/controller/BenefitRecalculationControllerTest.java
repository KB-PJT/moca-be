package com.moca.mocabe.domain.benefit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moca.mocabe.domain.benefit.dto.BenefitRecalculationResponse;
import com.moca.mocabe.domain.benefit.service.BenefitUsageCalculationService;
import com.moca.mocabe.global.auth.CurrentUserProvider;
import org.junit.jupiter.api.Test;

class BenefitRecalculationControllerTest {

  @Test
  void recalculatesOnlyForAuthenticatedUser() {
    BenefitUsageCalculationService service =
        org.mockito.Mockito.mock(BenefitUsageCalculationService.class);
    CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
    when(currentUser.getCurrentUserId()).thenReturn("user-1");
    when(service.recalculateForMonth("user-1", "2026-08")).thenReturn("2026-08");
    BenefitRecalculationController controller =
        new BenefitRecalculationController(service, currentUser);

    BenefitRecalculationResponse response = controller.recalculate("2026-08").getBody().getData();
    assertEquals(new BenefitRecalculationResponse("2026-08"), response);
    assertEquals("2026-08", response.yearMonth());
    verify(service).recalculateForMonth("user-1", "2026-08");
  }
}
