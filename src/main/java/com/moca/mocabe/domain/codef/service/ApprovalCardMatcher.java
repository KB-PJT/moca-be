package com.moca.mocabe.domain.codef.service;

import com.moca.mocabe.domain.codef.model.CodefApproval;
import com.moca.mocabe.domain.codef.model.UserCardMatchRow;
import java.util.List;

/**
 * CODEF 응답(승인내역·실적조회 등) 한 건을 사용자의 보유카드 중 하나에 매칭한다.
 *
 * 먼저 응답이 발생한 연동과 같은 카드사(issuerId)의 카드로 후보를 좁힌다. 카드사가 다르면 카드명이나
 * 마스킹 카드번호가 우연히 같아도 다른 카드이므로, 이 경계 없이는 엉뚱한 카드로 저장될 수 있다.
 * 그 다음 카드명(resCardName)이 있으면 ①정규화 원문 일치 → ②가장 많은 글자가 일치하는 카드 순으로 연결한다.
 * 신한카드처럼 카드명을 빈 값으로 주는 경우 마스킹된 카드번호(resCardNo)로 보유카드 번호와 대조한다.
 */
public class ApprovalCardMatcher {

    private final CardNameNormalizer cardNameNormalizer;

    public ApprovalCardMatcher(CardNameNormalizer cardNameNormalizer) {
        this.cardNameNormalizer = cardNameNormalizer;
    }

    /** issuerId는 이 승인내역을 조회한 CODEF 연동의 카드사다. 매칭되는 보유카드의 user_card_id를 반환하고, 매칭 실패 시 null. */
    public String match(List<UserCardMatchRow> cards, CodefApproval approval, String issuerId) {
        return match(cards, approval.cardName(), approval.cardNo(), issuerId);
    }

    /**
     * cardName/cardNo는 CODEF 응답(승인내역·실적조회 등)이 준 카드명·마스킹 카드번호다.
     * issuerId는 이 응답을 조회한 CODEF 연동의 카드사다. 매칭되는 보유카드의 user_card_id를 반환하고,
     * 매칭 실패 시 null.
     */
    public String match(List<UserCardMatchRow> cards, String cardName, String cardNo, String issuerId) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        List<UserCardMatchRow> sameIssuerCards = cards.stream()
                .filter(card -> card.issuerId() != null && card.issuerId().equals(issuerId))
                .toList();
        if (sameIssuerCards.isEmpty()) {
            return null;
        }
        if (cardName == null || cardName.isBlank()) {
            return matchByCardNo(sameIssuerCards, cardNo);
        }
        return matchByCardName(sameIssuerCards, cardName);
    }

    private String matchByCardName(List<UserCardMatchRow> cards, String codefCardName) {
        String target = cardNameNormalizer.normalize(codefCardName);
        if (target.isEmpty()) {
            return null;
        }
        // ① 정규화 원문 완전일치가 있으면 우선 채택한다.
        for (UserCardMatchRow card : cards) {
            if (cardNameNormalizer.normalize(card.cardName()).equals(target)) {
                return card.userCardId();
            }
        }
        // ② 완전일치가 없으면 겹치는 글자가 가장 긴 카드로 연결한다.
        //    승인명에 "KB국민 " 같은 발급사 접두사가 붙어 와도 보유카드명이 통째로 포함되면 잡히도록
        //    접두사 길이가 아닌 최장 공통 부분문자열 길이를 점수로 쓴다.
        String bestUserCardId = null;
        int bestScore = 0;
        for (UserCardMatchRow card : cards) {
            int score = longestCommonSubstringLength(target, cardNameNormalizer.normalize(card.cardName()));
            if (score > bestScore) {
                bestScore = score;
                bestUserCardId = card.userCardId();
            }
        }
        return bestUserCardId;
    }

    private String matchByCardNo(List<UserCardMatchRow> cards, String approvalCardNo) {
        String frontTarget = leadingDigits(approvalCardNo);
        String backTarget = trailingDigits(approvalCardNo);
        if (frontTarget.isEmpty() && backTarget.isEmpty()) {
            return null;
        }
        for (UserCardMatchRow card : cards) {
            if (sameMaskedCardNo(frontTarget, backTarget, card.cardNo())) {
                return card.userCardId();
            }
        }
        return null;
    }

    private boolean sameMaskedCardNo(String frontTarget, String backTarget, String cardNo) {
        if (cardNo == null || cardNo.isBlank()) {
            return false;
        }
        String front = leadingDigits(cardNo);
        String back = trailingDigits(cardNo);
        // 마스킹 위치가 달라도 앞자리·뒷자리 노출 숫자가 겹치는 만큼 모두 일치하면 같은 카드로 본다.
        return equalsOnOverlap(frontTarget, front, true) && equalsOnOverlap(backTarget, back, false)
                && (!frontTarget.isEmpty() || !backTarget.isEmpty());
    }

    private boolean equalsOnOverlap(String a, String b, boolean fromStart) {
        int length = Math.min(a.length(), b.length());
        if (length == 0) {
            // 비교할 노출 숫자가 한쪽도 없으면 이 구간은 판단에서 제외한다(다른 구간으로 판정).
            return true;
        }
        if (fromStart) {
            return a.regionMatches(0, b, 0, length);
        }
        return a.regionMatches(a.length() - length, b, b.length() - length, length);
    }

    private int longestCommonSubstringLength(String a, String b) {
        // 빈 문자열이면 DP 루프가 돌지 않아 자연스럽게 0을 반환한다.
        int[] previous = new int[b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    current[j] = previous[j - 1] + 1;
                    best = Math.max(best, current[j]);
                }
            }
            previous = current;
        }
        return best;
    }

    private String leadingDigits(String value) {
        if (value == null) {
            return "";
        }
        int index = 0;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return value.substring(0, index);
    }

    private String trailingDigits(String value) {
        if (value == null) {
            return "";
        }
        int index = value.length();
        while (index > 0 && Character.isDigit(value.charAt(index - 1))) {
            index--;
        }
        return value.substring(index);
    }
}
