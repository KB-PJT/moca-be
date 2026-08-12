-- Kakao 공식 그룹 코드는 내부 merchant taxonomy와 분리해 관리한다.
CREATE TABLE kakao_category_group_registry (
    kakao_category_group_code VARCHAR(8) NOT NULL,
    kakao_category_group_name VARCHAR(100) NOT NULL,
    benefit_mapping_supported BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (kakao_category_group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO kakao_category_group_registry
    (kakao_category_group_code, kakao_category_group_name, benefit_mapping_supported, note)
VALUES
    ('MT1', '대형마트', TRUE, NULL), ('CS2', '편의점', TRUE, NULL),
    ('PS3', '어린이집, 유치원', FALSE, '지도 표시 전용'), ('SC4', '학교', FALSE, '지도 표시 전용'),
    ('AC5', '학원', TRUE, NULL), ('PK6', '주차장', TRUE, NULL),
    ('OL7', '주유소, 충전소', TRUE, '상세 카테고리 패턴 필요'), ('SW8', '지하철역', TRUE, NULL),
    ('BK9', '은행', FALSE, '지도 표시 전용'), ('CT1', '문화시설', TRUE, '상세 카테고리 패턴 필요'),
    ('AG2', '중개업소', FALSE, '지도 표시 전용'), ('PO3', '공공기관', FALSE, '지도 표시 전용'),
    ('AT4', '관광명소', FALSE, '지도 표시 전용'), ('AD5', '숙박', TRUE, NULL),
    ('FD6', '음식점', TRUE, NULL), ('CE7', '카페', TRUE, NULL),
    ('HP8', '병원', TRUE, '세부 진료 카테고리 패턴 필요'), ('PM9', '약국', TRUE, NULL);

-- DISPLAY_ONLY는 모든 공식 그룹에 허용하고, ALLOW는 지원 대상으로 검토한 그룹에만 허용한다.
-- kakao_category_maps의 복합 FK가 이 표를 참조하므로 unsupported 그룹의 ALLOW 저장은 DB에서 실패한다.
CREATE TABLE kakao_category_group_policies (
    kakao_category_group_code VARCHAR(8) NOT NULL,
    benefit_match_policy VARCHAR(20) NOT NULL,
    PRIMARY KEY (kakao_category_group_code, benefit_match_policy),
    CONSTRAINT fk_kakao_group_policies_registry
        FOREIGN KEY (kakao_category_group_code)
        REFERENCES kakao_category_group_registry (kakao_category_group_code),
    CONSTRAINT chk_kakao_group_policies_policy
        CHECK (benefit_match_policy IN ('ALLOW', 'DISPLAY_ONLY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO kakao_category_group_policies
    (kakao_category_group_code, benefit_match_policy)
SELECT kakao_category_group_code, 'DISPLAY_ONLY'
FROM kakao_category_group_registry;

INSERT INTO kakao_category_group_policies
    (kakao_category_group_code, benefit_match_policy)
SELECT kakao_category_group_code, 'ALLOW'
FROM kakao_category_group_registry
WHERE benefit_mapping_supported = TRUE;

ALTER TABLE kakao_category_maps
    ADD COLUMN match_method VARCHAR(32) NOT NULL DEFAULT 'GROUP_CODE'
        AFTER kakao_category_name_pattern,
    ADD COLUMN confidence_score DECIMAL(4,3) NOT NULL DEFAULT 0.800 AFTER match_method,
    ADD COLUMN benefit_match_policy VARCHAR(20) NOT NULL DEFAULT 'DISPLAY_ONLY'
        AFTER confidence_score,
    ADD CONSTRAINT fk_kakao_category_maps_group_registry
        FOREIGN KEY (kakao_category_group_code)
        REFERENCES kakao_category_group_registry (kakao_category_group_code),
    ADD CONSTRAINT fk_kakao_category_maps_allowed_policy
        FOREIGN KEY (kakao_category_group_code, benefit_match_policy)
        REFERENCES kakao_category_group_policies
            (kakao_category_group_code, benefit_match_policy),
    ADD CONSTRAINT chk_kakao_category_maps_match_method
        CHECK (match_method IN ('GROUP_CODE', 'GROUP_AND_PATTERN', 'NAME_PATTERN')),
    ADD CONSTRAINT chk_kakao_category_maps_confidence
        CHECK (confidence_score BETWEEN 0.000 AND 1.000),
    ADD CONSTRAINT chk_kakao_category_maps_policy
        CHECK (benefit_match_policy IN ('ALLOW', 'DISPLAY_ONLY')),
    ADD INDEX idx_kakao_category_maps_resolution
        (kakao_category_group_code, enabled, benefit_match_policy, priority);

-- 기존 행은 검토 전까지 계산에 사용하지 않는다. 데이터 검증 후 ALLOW로 명시 승격한다.
UPDATE kakao_category_maps SET benefit_match_policy = 'DISPLAY_ONLY';
