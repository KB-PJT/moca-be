-- 보유 카드 실적 스냅샷. CODEF 실적조회(result-check-list)로 조회한 달의 현재 인정 실적금액을 저장한다.
-- 카드 한 장에 혜택별로 여러 실적 리스트(resCardPerformanceList)가 나올 수 있는데, 그중 가장 큰
-- resCurrentUseAmt(현재이용금액)를 그 달의 대표 실적으로 저장한다(CardSyncService).
-- 동기화를 다시 돌리면 같은 달의 값을 최신 값으로 덮어써야 하므로 (user_card_id, performance_month)
-- UNIQUE로 두고 애플리케이션에서 upsert(INSERT ... ON DUPLICATE KEY UPDATE)한다.

CREATE TABLE user_card_performance_snapshots (
    performance_snapshot_id CHAR(36) NOT NULL,
    user_card_id CHAR(36) NOT NULL,
    performance_month CHAR(7) NOT NULL,
    current_spend_amount INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (performance_snapshot_id),
    CONSTRAINT uk_user_card_performance_snapshots_card_month
        UNIQUE (user_card_id, performance_month),
    CONSTRAINT fk_user_card_performance_snapshots_user_card
        FOREIGN KEY (user_card_id) REFERENCES user_cards (user_card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- CODEF 실적조회(result-check-list)는 카드사마다 startDate를 몇 달 전까지 허용하는지가 다르다
-- (예: KB/하나 최근 12개월, 우리 최근 3개월, 신한 당월만). NULL은 "확인된 정책 없음"을 뜻하며,
-- 이 경우 CardSyncService는 이번 달(0개월 전)까지만 조회 가능한 것으로 보수적으로 처리한다.
-- -1은 "실적조회 자체를 지원하지 않는 카드사로 확인됨"이라는 별도 의미이며(0=당월만 지원과 구분),
-- CardSyncService가 이 값이면 CODEF를 호출하지 않고 응답의 unsupportedPerformanceIssuers로 알린다.
ALTER TABLE issuers
    ADD COLUMN performance_lookback_months SMALLINT NULL,
    ADD CONSTRAINT chk_issuers_performance_lookback_months
        CHECK (performance_lookback_months IS NULL OR performance_lookback_months >= -1);
