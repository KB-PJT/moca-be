package com.moca.mocabe.domain.codef.exception;

/**
 * 카드번호가 필요한 카드사에서 POST /card-links/{linkId}/cards/discover(2단계 보유카드 조회)를
 * 이미 성공적으로 소비한 연동에 다시 호출했을 때 발생한다. pending 카드번호/비밀번호는 1회성이라
 * 재시도가 필요하면 POST /card-links/cards/sync(재조회)를 대신 써야 한다.
 */
public class CardLinkAlreadyDiscoveredException extends RuntimeException {

    public CardLinkAlreadyDiscoveredException() {
        super("이미 보유카드 조회를 완료했습니다. 재조회는 POST /card-links/cards/sync를 사용하세요.");
    }
}
