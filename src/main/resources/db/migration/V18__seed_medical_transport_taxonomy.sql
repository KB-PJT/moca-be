-- 혜택 판정에 필요한 최소 의료/교통 taxonomy. 기존 운영 카테고리가 있으면 재사용한다.
INSERT INTO merchant_categories
    (merchant_category_id, parent_id, category_code, category_name, display_order,
     is_map_visible, created_at, updated_at)
SELECT UUID(), NULL, seed.category_code, seed.category_name, seed.display_order,
       TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 'HOSPITAL' category_code, '병원' category_name, 200 display_order
    UNION ALL SELECT 'PHARMACY', '약국', 220
    UNION ALL SELECT 'PUBLIC_TRANSIT', '대중교통', 300
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM merchant_categories current_category
    WHERE current_category.category_code = seed.category_code
);

INSERT INTO merchant_categories
    (merchant_category_id, parent_id, category_code, category_name, display_order,
     is_map_visible, created_at, updated_at)
SELECT UUID(), NULL, seed.category_code, seed.category_name,
       seed.display_order, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 'HOSPITAL' parent_code, 'GENERAL_HOSPITAL' category_code, '종합병원' category_name, 201 display_order
    UNION ALL SELECT 'HOSPITAL', 'CLINIC', '의원', 202
    UNION ALL SELECT 'HOSPITAL', 'DENTAL', '치과', 203
    UNION ALL SELECT 'HOSPITAL', 'ORIENTAL_MEDICINE', '한의원·한방병원', 204
    UNION ALL SELECT 'HOSPITAL', 'VETERINARY', '동물병원', 205
    UNION ALL SELECT 'HOSPITAL', 'NURSING_HOSPITAL', '요양병원', 206
    UNION ALL SELECT 'HOSPITAL', 'PUBLIC_HEALTH_CENTER', '보건소', 207
    UNION ALL SELECT 'HOSPITAL', 'DERMATOLOGY', '피부과', 208
    UNION ALL SELECT 'HOSPITAL', 'PLASTIC_SURGERY', '성형외과', 209
    UNION ALL SELECT 'HOSPITAL', 'POSTPARTUM_CARE_CENTER', '산후조리원', 210
    UNION ALL SELECT 'PUBLIC_TRANSIT', 'BUS', '버스', 301
    UNION ALL SELECT 'PUBLIC_TRANSIT', 'SUBWAY', '지하철', 302
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM merchant_categories current_category
    WHERE current_category.category_code = seed.category_code
);

-- HP8는 상세 category_name이 식별되는 경우에만 계산 가능하다. 포괄 HOSPITAL fallback은 만들지 않는다.
INSERT INTO kakao_category_maps
    (kakao_category_map_id, merchant_category_id, kakao_category_group_code,
     kakao_category_name_pattern, match_method, confidence_score, benefit_match_policy,
     priority, enabled, created_at, updated_at)
SELECT UUID(), category.merchant_category_id, seed.group_code, seed.name_pattern,
       seed.match_method, seed.confidence_score, seed.match_policy,
       seed.priority, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 'VETERINARY' category_code, 'HP8' group_code, '동물병원' name_pattern,
           'GROUP_AND_PATTERN' match_method, 0.990 confidence_score, 'ALLOW' match_policy, 1 priority
    UNION ALL SELECT 'DENTAL', 'HP8', '치과', 'GROUP_AND_PATTERN', 0.990, 'ALLOW', 2
    UNION ALL SELECT 'ORIENTAL_MEDICINE', 'HP8', '한의원', 'GROUP_AND_PATTERN', 0.990, 'ALLOW', 3
    UNION ALL SELECT 'ORIENTAL_MEDICINE', 'HP8', '한방병원', 'GROUP_AND_PATTERN', 0.990, 'ALLOW', 4
    UNION ALL SELECT 'NURSING_HOSPITAL', 'HP8', '요양병원', 'GROUP_AND_PATTERN', 0.990, 'ALLOW', 5
    UNION ALL SELECT 'PUBLIC_HEALTH_CENTER', 'HP8', '보건소', 'GROUP_AND_PATTERN', 0.990, 'ALLOW', 6
    UNION ALL SELECT 'DERMATOLOGY', 'HP8', '피부과', 'GROUP_AND_PATTERN', 0.970, 'ALLOW', 7
    UNION ALL SELECT 'PLASTIC_SURGERY', 'HP8', '성형외과', 'GROUP_AND_PATTERN', 0.970, 'ALLOW', 8
    UNION ALL SELECT 'GENERAL_HOSPITAL', 'HP8', '종합병원', 'GROUP_AND_PATTERN', 0.990, 'ALLOW', 9
    UNION ALL SELECT 'CLINIC', 'HP8', '의원', 'GROUP_AND_PATTERN', 0.900, 'ALLOW', 10
    UNION ALL SELECT 'PHARMACY', 'PM9', '', 'GROUP_CODE', 1.000, 'ALLOW', 1
    UNION ALL SELECT 'SUBWAY', 'SW8', '', 'GROUP_CODE', 1.000, 'ALLOW', 1
) seed
JOIN merchant_categories category ON category.category_code = seed.category_code
WHERE NOT EXISTS (
    SELECT 1 FROM kakao_category_maps current_map
    WHERE current_map.merchant_category_id = category.merchant_category_id
      AND current_map.kakao_category_group_code = seed.group_code
      AND current_map.kakao_category_name_pattern = seed.name_pattern
);
