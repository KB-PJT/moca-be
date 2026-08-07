package com.moca.mocabe.domain.codef.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.domain.codef.model.CodefApproval;
import com.moca.mocabe.domain.codef.model.UserCardMatchRow;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalCardMatcherTest {

    private static final String ISSUER_1 = "issuer-1";
    private static final String ISSUER_2 = "issuer-2";

    private final ApprovalCardMatcher matcher = new ApprovalCardMatcher(new CardNameNormalizer());

    @Test
    @DisplayName("카드명이 정규화 기준으로 완전일치하면 해당 카드에 연결한다")
    void matchesByExactName() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "KB My WE:SH", "1234****5678"),
                new UserCardMatchRow("uc-2", ISSUER_1, "삼성 taptap O", "9876****5432"));

        assertEquals("uc-1", matcher.match(cards, approvalWithName("kb  my wesh"), ISSUER_1));
    }

    @Test
    @DisplayName("완전일치가 없으면 글자가 가장 많이 일치하는 카드에 연결한다")
    void matchesByLongestCommonPrefix() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "노리 체크카드", "1234****5678"),
                new UserCardMatchRow("uc-2", ISSUER_1, "노리2 체크카드(KB Pay)", "9876****5432"));

        assertEquals("uc-2",
                matcher.match(cards, approvalWithName("KB국민 노리2 체크카드(KB Pay)_비교통"), ISSUER_1));
    }

    @Test
    @DisplayName("승인명에 발급사 접두사가 붙어도 보유카드명이 포함되면 매칭한다")
    void matchesWhenApprovalNameHasIssuerPrefix() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "노리2 체크카드(KB Pay)_비교통", "943646******1069"));

        assertEquals("uc-1",
                matcher.match(cards, approvalWithName("KB국민 노리2 체크카드(KB Pay)_비교통"), ISSUER_1));
    }

    @Test
    @DisplayName("카드명이 비어 있으면 마스킹 카드번호 앞뒤 숫자로 매칭한다")
    void matchesByMaskedCardNoWhenNameBlank() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "카드 A", "943646******1069"),
                new UserCardMatchRow("uc-2", ISSUER_1, "카드 B", "123456******9999"));

        CodefApproval approval = new CodefApproval(
                "20260801", "120000", "943646******1069", "", "가맹점", "10000", "1", "1", "1", "{}");

        assertEquals("uc-1", matcher.match(cards, approval, ISSUER_1));
    }

    @Test
    @DisplayName("카드명이 비고 카드번호도 일치하지 않으면 매칭 실패로 null을 반환한다")
    void returnsNullWhenNoCardNoMatch() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "카드 A", "943646******1069"));

        CodefApproval approval = new CodefApproval(
                "20260801", "120000", "555555******0000", "", "가맹점", "10000", "1", "1", "1", "{}");

        assertNull(matcher.match(cards, approval, ISSUER_1));
    }

    @Test
    @DisplayName("카드번호가 비어 있는 승인은 카드번호 매칭에서 제외된다")
    void returnsNullWhenApprovalCardNoBlank() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "카드 A", "943646******1069"));

        CodefApproval approval = new CodefApproval(
                "20260801", "120000", "", "", "가맹점", "10000", "1", "1", "1", "{}");

        assertNull(matcher.match(cards, approval, ISSUER_1));
    }

    @Test
    @DisplayName("보유카드 번호가 없으면 카드번호 매칭 대상에서 제외한다")
    void skipsCardWithoutStoredCardNo() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "카드 A", null),
                new UserCardMatchRow("uc-2", ISSUER_1, "카드 B", "943646******1069"));

        CodefApproval approval = new CodefApproval(
                "20260801", "120000", "943646******1069", "", "가맹점", "10000", "1", "1", "1", "{}");

        assertEquals("uc-2", matcher.match(cards, approval, ISSUER_1));
    }

    @Test
    @DisplayName("앞자리가 마스킹된 카드번호는 뒷자리만으로 매칭한다")
    void matchesByTrailingDigitsWhenFrontMasked() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "카드 A", "****1069"),
                new UserCardMatchRow("uc-2", ISSUER_1, "카드 B", "****9999"));

        CodefApproval approval = new CodefApproval(
                "20260801", "120000", "****1069", "", "가맹점", "10000", "1", "1", "1", "{}");

        assertEquals("uc-1", matcher.match(cards, approval, ISSUER_1));
    }

    @Test
    @DisplayName("카드명과 카드번호가 모두 비면 매칭하지 않는다")
    void returnsNullWhenNameAndCardNoBothMissing() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "카드 A", "943646******1069"));

        CodefApproval approval = new CodefApproval(
                "20260801", "120000", null, "", "가맹점", "10000", "1", "1", "1", "{}");

        assertNull(matcher.match(cards, approval, ISSUER_1));
    }

    @Test
    @DisplayName("보유카드가 없으면 null을 반환한다")
    void returnsNullWhenNoCards() {
        assertNull(matcher.match(List.of(), approvalWithName("카드"), ISSUER_1));
    }

    @Test
    @DisplayName("정규화 결과가 비는 카드명은 매칭하지 않는다")
    void returnsNullWhenNormalizedNameEmpty() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "카드 A", "1234****5678"));

        assertNull(matcher.match(cards, approvalWithName("()"), ISSUER_1));
    }

    @Test
    @DisplayName("카드명이 동일해도 다른 카드사의 카드는 매칭 후보에서 제외한다")
    void excludesCardsFromDifferentIssuerByName() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_2, "노리2 체크카드(KB Pay)", "1234****5678"));

        assertNull(matcher.match(cards, approvalWithName("노리2 체크카드(KB Pay)"), ISSUER_1));
    }

    @Test
    @DisplayName("마스킹 카드번호가 동일해도 다른 카드사의 카드는 매칭 후보에서 제외한다")
    void excludesCardsFromDifferentIssuerByCardNo() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_2, "카드 A", "943646******1069"));

        CodefApproval approval = new CodefApproval(
                "20260801", "120000", "943646******1069", "", "가맹점", "10000", "1", "1", "1", "{}");

        assertNull(matcher.match(cards, approval, ISSUER_1));
    }

    @Test
    @DisplayName("여러 카드사 후보가 섞여 있어도 같은 카드사 카드만으로 매칭한다")
    void matchesOnlyWithinSameIssuerWhenMixed() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_2, "노리2 체크카드(KB Pay)", "1234****5678"),
                new UserCardMatchRow("uc-2", ISSUER_1, "노리2 체크카드(KB Pay)", "9999****0000"));

        assertEquals("uc-2", matcher.match(cards, approvalWithName("노리2 체크카드(KB Pay)"), ISSUER_1));
    }

    @Test
    @DisplayName("승인내역이 아닌 카드명·카드번호를 직접 넘겨도 같은 규칙으로 매칭한다(실적조회 등 재사용)")
    void matchesWithRawCardNameAndCardNo() {
        List<UserCardMatchRow> cards = List.of(
                new UserCardMatchRow("uc-1", ISSUER_1, "노리2 체크카드(KB Pay)_비교통", "943646******1069"));

        assertEquals("uc-1", matcher.match(cards, "노리2 체크카드(KB Pay)_비교통", "943646******1069", ISSUER_1));
    }

    private CodefApproval approvalWithName(String cardName) {
        return new CodefApproval(
                "20260801", "120000", "1234****5678", cardName, "가맹점", "10000", "1", "1", "1", "{}");
    }
}
