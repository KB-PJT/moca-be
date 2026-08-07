package com.moca.mocabe.domain.report.dto;

/** 리포트에서 표시하는 사용자 카드의 최소 정보다. */
public record ReportUserCardResponse(String userCardId, String cardName, String cardColor) {
}
