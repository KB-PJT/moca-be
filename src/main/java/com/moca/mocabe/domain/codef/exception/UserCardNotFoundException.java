package com.moca.mocabe.domain.codef.exception;

/** 카드번호/비밀번호를 추가 입력하려는 user_card_id가 이 사용자 소유가 아니거나 존재하지 않을 때 발생한다. */
public class UserCardNotFoundException extends RuntimeException {

    public UserCardNotFoundException() {
        super("카드를 찾을 수 없습니다.");
    }
}
