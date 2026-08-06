package com.moca.mocabe.domain.codef.model;

/**
 * CODEF 실적조회(result-check-list) 응답 카드 한 장의 내부 모델이다.
 *
 * 카드 한 장에 혜택별로 여러 실적 리스트(resCardPerformanceList)가 나올 수 있는데, currentSpendAmount는
 * 그중 가장 큰 resCurrentUseAmt(현재이용금액)로 CodefClient가 미리 골라 담는다.
 * 실적 리스트가 하나도 없으면(혜택이 없거나 파싱 가능한 금액이 없으면) null이다.
 */
public record CodefCardPerformance(
        String cardName,
        String cardNo,
        Integer currentSpendAmount
) {
}
