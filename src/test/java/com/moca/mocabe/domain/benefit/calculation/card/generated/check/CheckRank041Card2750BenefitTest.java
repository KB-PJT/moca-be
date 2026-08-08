package com.moca.mocabe.domain.benefit.calculation.card.generated.check;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moca.mocabe.domain.benefit.calculation.card.CardBenefitTestFixture;


@DisplayName("체크 41위 달달 하나 체크카드")
class CheckRank041Card2750BenefitTest {

    private final CardBenefitTestFixture fixture = new CardBenefitTestFixture();

    @Test
    @DisplayName("수수료우대: 직접 카드 결제 계산 규칙 검토 필요 - 계산 가능한 할인/캐시백/포인트/마일리지 패턴을 자동 추출하지 못함")
    void classifiesBenefit001() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2750",
                /* 카드 내 혜택 순번 */ 1,
                /* benefit_title */ "수수료우대",
                /* 계산 지원 상태 */ "DIRECT_OFFLINE_REVIEW_REQUIRED",
                /* 분류 사유 */ "계산 가능한 할인/캐시백/포인트/마일리지 패턴을 자동 추출하지 못함");
    }

    @Test
    @DisplayName("적립: 온라인·간편결제 범위 제외 - 온라인·앱·배달·간편결제·자동납부 전용 혜택")
    void classifiesBenefit002() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2750",
                /* 카드 내 혜택 순번 */ 2,
                /* benefit_title */ "적립",
                /* 계산 지원 상태 */ "ONLINE_OR_INDIRECT_EXCLUDED",
                /* 분류 사유 */ "온라인·앱·배달·간편결제·자동납부 전용 혜택");
    }

    @Test
    @DisplayName("유의사항: 계산 규칙이 아닌 상세 정보 - 유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보")
    void classifiesBenefit003() {
        // 계산하지 않는 상세도 누락하지 않고 원본 분류 상태와 제외·검토 사유를 고정한다.
        fixture.assertSourceDetail(
                /* 카드고릴라 카드 ID */ "2750",
                /* 카드 내 혜택 순번 */ 3,
                /* benefit_title */ "유의사항",
                /* 계산 지원 상태 */ "NON_RULE_DETAIL",
                /* 분류 사유 */ "유의사항·발급·연회비 등 계산 산식이 아닌 상세 정보");
    }


}
