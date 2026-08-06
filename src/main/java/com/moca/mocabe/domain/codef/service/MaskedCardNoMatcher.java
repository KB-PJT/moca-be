package com.moca.mocabe.domain.codef.service;

/**
 * CODEF가 주는 마스킹된 카드번호(예 943646******1069)와 전체 카드번호(또는 다른 마스킹 카드번호)를
 * 비교하는 유틸이다. 마스킹 위치가 달라도 양쪽에 노출된 앞자리·뒷자리 숫자가 겹치는 구간에서
 * 모두 일치하면 같은 카드로 본다. {@link ApprovalCardMatcher}(승인내역·실적 매칭)와
 * {@code CardLinkService}(계정 생성 시 입력한 카드번호로 보유카드 매칭)가 함께 쓴다.
 */
final class MaskedCardNoMatcher {

    private MaskedCardNoMatcher() {
    }

    /** a/b 둘 다 마스킹돼 있거나, 한쪽만 마스킹돼 있어도(전체 카드번호 vs 마스킹 카드번호) 비교할 수 있다. */
    static boolean matches(String a, String b) {
        String frontA = leadingDigits(a);
        String backA = trailingDigits(a);
        if (frontA.isEmpty() && backA.isEmpty()) {
            return false;
        }
        String frontB = leadingDigits(b);
        String backB = trailingDigits(b);
        if (frontB.isEmpty() && backB.isEmpty()) {
            return false;
        }
        return equalsOnOverlap(frontA, frontB, true) && equalsOnOverlap(backA, backB, false);
    }

    private static boolean equalsOnOverlap(String a, String b, boolean fromStart) {
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

    private static String leadingDigits(String value) {
        if (value == null) {
            return "";
        }
        int index = 0;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return value.substring(0, index);
    }

    private static String trailingDigits(String value) {
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
