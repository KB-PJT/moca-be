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
 * ②는 겹치는 글자가 두 이름 중 짧은 쪽 길이의 {@link #MIN_FUZZY_MATCH_RATIO} 이상이어야 채택한다(같은
 * 카드사 후보가 한 장뿐이면 이름이 거의 안 겹쳐도 무조건 그 카드로 연결돼버리는 문제를 막기 위함).
 * 카드명이 비어 있거나(신한카드 등) 이름 매칭이 기준을 못 넘기면, 마스킹된 카드번호(resCardNo)로
 * 보유카드 번호와 대조한다.
 */
public class ApprovalCardMatcher {

    // 카드명 유사 매칭(②)에서 최소로 요구하는 공통 부분문자열 비율이다. 두 이름 중 짧은 쪽 길이 대비
    // 이 비율 미만이면(예: 10글자 카드명에 2글자만 우연히 겹침) 매칭 실패로 처리해 엉뚱한 카드로
    // 연결되는 것을 막는다.
    private static final double MIN_FUZZY_MATCH_RATIO = 0.5;

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
        // 카드명으로 매칭을 시도하고, 이름이 없거나 기준을 못 넘겨 실패하면 마스킹 카드번호로 한 번 더 시도한다.
        String matchedByName =
                (cardName == null || cardName.isBlank()) ? null : matchByCardName(sameIssuerCards, cardName);
        if (matchedByName != null) {
            return matchedByName;
        }
        return matchByCardNo(sameIssuerCards, cardNo);
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
        // ② 완전일치가 없으면 겹치는 글자가 가장 긴 카드로 연결하되, 겹치는 길이가 두 이름 중 짧은 쪽의
        //    MIN_FUZZY_MATCH_RATIO 이상인 후보만 candidate로 본다. 승인명에 "KB국민 " 같은 발급사
        //    접두사가 붙어 와도 보유카드명이 통째로 포함되면 잡히도록 접두사 길이가 아닌 최장 공통
        //    부분문자열 길이를 점수로 쓴다.
        String bestUserCardId = null;
        int bestScore = 0;
        for (UserCardMatchRow card : cards) {
            String candidateName = cardNameNormalizer.normalize(card.cardName());
            int minLength = Math.min(target.length(), candidateName.length());
            if (minLength == 0) {
                continue;
            }
            int score = longestCommonSubstringLength(target, candidateName);
            if (score < minLength * MIN_FUZZY_MATCH_RATIO) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                bestUserCardId = card.userCardId();
            }
        }
        return bestUserCardId;
    }

    private String matchByCardNo(List<UserCardMatchRow> cards, String approvalCardNo) {
        for (UserCardMatchRow card : cards) {
            if (MaskedCardNoMatcher.matches(approvalCardNo, card.cardNo())) {
                return card.userCardId();
            }
        }
        return null;
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
}
