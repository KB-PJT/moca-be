-- 카드고릴라의 원본 카테고리는 내부 가맹점 분류로 덮어쓰지 않고 별도로 보존한다.
ALTER TABLE card_benefits
    ADD COLUMN source_category_id INT UNSIGNED NULL AFTER position,
    ADD INDEX idx_card_benefits_source_category (source_category_id);

-- 카드고릴라 한 카테고리가 여러 내부 카테고리에 대응할 수 있다.
CREATE TABLE card_gorilla_category_maps (
    card_gorilla_category_map_id CHAR(36) NOT NULL,
    source_category_id INT UNSIGNED NOT NULL,
    merchant_category_id CHAR(36) NOT NULL,
    priority SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (card_gorilla_category_map_id),
    CONSTRAINT fk_card_gorilla_category_maps_category
        FOREIGN KEY (merchant_category_id) REFERENCES merchant_categories (merchant_category_id),
    CONSTRAINT uk_card_gorilla_category_maps_source_category
        UNIQUE (source_category_id, merchant_category_id),
    INDEX idx_card_gorilla_category_maps_source (source_category_id, enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
