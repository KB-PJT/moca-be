package com.moca.mocabe.domain.notification.model;

import java.math.BigDecimal;

public record PerformanceDeadlineCandidate(String userId, String userCardId, String cardName,
                                           BigDecimal currentSpendAmount,
                                           BigDecimal requiredSpendAmount) { }
