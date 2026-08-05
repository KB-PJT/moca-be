package com.moca.mocabe.domain.card.dto;

/** POST /me/cards/sync 응답. syncedAt은 KST 오프셋(+09:00)이 포함된 ISO-8601 문자열이다. */
public class SyncMyCardsResponse {

    private final int syncedCardCount;
    private final int syncedApprovalCount;
    private final String syncedAt;

    public SyncMyCardsResponse(int syncedCardCount, int syncedApprovalCount, String syncedAt) {
        this.syncedCardCount = syncedCardCount;
        this.syncedApprovalCount = syncedApprovalCount;
        this.syncedAt = syncedAt;
    }

    public int getSyncedCardCount() {
        return syncedCardCount;
    }

    public int getSyncedApprovalCount() {
        return syncedApprovalCount;
    }

    public String getSyncedAt() {
        return syncedAt;
    }
}
