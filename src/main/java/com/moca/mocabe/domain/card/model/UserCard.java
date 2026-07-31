package com.moca.mocabe.domain.card.model;

import java.time.LocalDateTime;

/** user_cards 테이블의 사용자 보유 카드 데이터를 나타내는 MyBatis 모델이다. */
public class UserCard {

    private String userCardId;
    private String userId;
    private String cardId;
    private String codefConnectionId;
    private String issuerId;
    private String cardNameFromCodef;
    private Integer displayOrder;
    private boolean active;
    private String codefCardKeyHash;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getUserCardId() {
        return userCardId;
    }

    public void setUserCardId(String userCardId) {
        this.userCardId = userCardId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCodefConnectionId() {
        return codefConnectionId;
    }

    public void setCodefConnectionId(String codefConnectionId) {
        this.codefConnectionId = codefConnectionId;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getCardNameFromCodef() {
        return cardNameFromCodef;
    }

    public void setCardNameFromCodef(String cardNameFromCodef) {
        this.cardNameFromCodef = cardNameFromCodef;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCodefCardKeyHash() {
        return codefCardKeyHash;
    }

    public void setCodefCardKeyHash(String codefCardKeyHash) {
        this.codefCardKeyHash = codefCardKeyHash;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
