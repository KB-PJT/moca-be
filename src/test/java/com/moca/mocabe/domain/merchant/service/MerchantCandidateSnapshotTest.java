package com.moca.mocabe.domain.merchant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moca.mocabe.domain.merchant.model.MerchantNameCandidate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantCandidateSnapshotTest {

    private final MerchantNameNormalizer normalizer = new MerchantNameNormalizer();

    @Test
    @DisplayName("가맹점명이 승인명의 접두사이면 매칭한다")
    void resolvesByPrefixMatch() {
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(new MerchantNameCandidate("m-1", "스타벅스")), List.of());

        assertEquals("m-1", snapshot.resolveMerchantId("스타벅스 강남점"));
    }

    @Test
    @DisplayName("접두사 후보가 여러 개면 가장 긴(구체적인) 것을 채택한다")
    void resolvesLongestPrefixMatch() {
        MerchantCandidateSnapshot snapshot = snapshot(List.of(
                new MerchantNameCandidate("m-1", "메가"),
                new MerchantNameCandidate("m-2", "메가커피")), List.of());

        assertEquals("m-2", snapshot.resolveMerchantId("메가커피 어린이대공원역점"));
    }

    @Test
    @DisplayName("접두사가 아니라 꼬리에 들어있는 가맹점명은 매칭하지 않는다")
    void doesNotMatchWhenNameIsInTail() {
        // 승인명 "메가커피어린이대공원역점"의 꼬리에 '어린이대공원'이 있어도 접두사가 아니므로 탈락한다.
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(new MerchantNameCandidate("m-1", "어린이대공원")), List.of());

        assertNull(snapshot.resolveMerchantId("메가커피어린이대공원역점"));
    }

    @Test
    @DisplayName("2글자 짧은 브랜드도 접두사로 매칭된다")
    void matchesShortBrandPrefix() {
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(new MerchantNameCandidate("m-1", "CU")), List.of());

        assertEquals("m-1", snapshot.resolveMerchantId("CU강남역점"));
    }

    @Test
    @DisplayName("merchants에서 접두사가 매칭되면 alias는 보지 않고 그 merchant_id를 반환한다")
    void resolvesFromMerchantsFirst() {
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(new MerchantNameCandidate("m-1", "메가커피")),
                List.of(new MerchantNameCandidate("m-2", "메가커피")));

        assertEquals("m-1", snapshot.resolveMerchantId("메가커피 어린이대공원역점"));
    }

    @Test
    @DisplayName("merchants에서 못 찾으면 merchant_aliases에서 접두사로 찾는다")
    void fallsBackToAliases() {
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(), List.of(new MerchantNameCandidate("m-2", "메가엠지씨MGC커피")));

        assertEquals("m-2", snapshot.resolveMerchantId("메가엠지씨MGC커피"));
    }

    @Test
    @DisplayName("별칭도 가장 긴(구체적인) 접두사를 채택한다")
    void resolvesLongestAliasPrefixMatch() {
        MerchantCandidateSnapshot snapshot = snapshot(List.of(), List.of(
                new MerchantNameCandidate("m-generic", "메가"),
                new MerchantNameCandidate("m-specific", "메가엠지씨MGC커피")));

        assertEquals("m-specific", snapshot.resolveMerchantId("메가엠지씨MGC커피역삼점"));
    }

    @Test
    @DisplayName("이름이 비면 null을 반환한다")
    void returnsNullForBlankName() {
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(new MerchantNameCandidate("m-1", "스타벅스")), List.of());

        assertNull(snapshot.resolveMerchantId("  "));
    }

    @Test
    @DisplayName("DB에 저장된 후보명이 정규화 규칙과 어긋나도(소문자·특수문자) 재정규화해서 매칭한다")
    void reNormalizesStoredCandidateNameBeforeMatching() {
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(new MerchantNameCandidate("m-1", "gs25")), List.of());

        assertEquals("m-1", snapshot.resolveMerchantId("GS25 화양세종점"));
    }

    @Test
    @DisplayName("merchants·aliases 모두 일치하지 않으면 null을 반환한다")
    void returnsNullWhenNoMatch() {
        MerchantCandidateSnapshot snapshot = snapshot(
                List.of(new MerchantNameCandidate("m-1", "스타벅스")), List.of());

        assertNull(snapshot.resolveMerchantId("미등록가맹점"));
    }

    private MerchantCandidateSnapshot snapshot(List<MerchantNameCandidate> nameCandidates,
                                               List<MerchantNameCandidate> aliasCandidates) {
        return new MerchantCandidateSnapshot(nameCandidates, aliasCandidates, normalizer);
    }
}
