package com.moca.mocabe.domain.codef.exception;

import com.moca.mocabe.domain.codef.model.CardCredentialIssue;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 카드사가 요구하는 카드번호/비밀번호가 저장돼 있지 않은 카드를 활성화하려 할 때 발생한다.
 * 계정 생성 단계의 {@link CodefCredentialRequiredException}과 달리, 특정 카드(user_card_id)에
 * 대한 것이라 PATCH /card-links/cards/{userCardId}/credentials로 값을 채운 뒤 다시 활성화해야 한다.
 * 여러 카드를 한 번에 활성화하는 요청이면 문제 있는 카드를 모두 모아 issues에 담는다(하나면 한 건만).
 */
public class CardCredentialRequiredException extends RuntimeException {

    private final List<CardCredentialIssue> issues;

    public CardCredentialRequiredException(List<CardCredentialIssue> issues) {
        super("카드 활성화에 필요한 카드번호/비밀번호가 없는 카드가 있습니다. userCardIds="
                + issues.stream().map(CardCredentialIssue::userCardId).collect(Collectors.joining(", ")));
        this.issues = Collections.unmodifiableList(issues);
    }

    public List<CardCredentialIssue> getIssues() {
        return issues;
    }
}
