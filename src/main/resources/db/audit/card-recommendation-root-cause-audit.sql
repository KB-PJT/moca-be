-- MySQL 8 / 카드 단위 추천 Root Cause 감사
-- READY + OUT_OF_SCOPE + BLOCKED(primary_blocker별) 합계는 항상 전체 카드 수와 같아야 한다.

WITH card_evidence AS (
    SELECT card.card_id,
           card.gorilla_card_id,
           issuer.issuer_name,
           version.name AS card_name,
           MAX(benefit.record_type = 'benefit') AS has_benefit,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '편의점|카페|커피|음식점|외식|마트|백화점|영화|병원|약국|학원|주유|충전|'
               '택시|버스|지하철|대중교통|베이커리|패스트푸드|테마파크|미용|'
               '모든가맹점|국내가맹점|국내외가맹점')
               AS has_offline_monetary_candidate,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '온라인|앱 결제|통신요금|공과금|정기결제|해외|항공|라운지|바우처')
               AS has_out_of_scope_evidence,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               'GS ?25|지에스25|CU|씨유|스타벅스|STARBUCKS|CGV|씨지브이|롯데시네마|'
               '메가박스|올리브영|이마트|홈플러스|롯데마트|파리바게뜨|뚜레쥬르')
               AS has_merchant_candidate,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '간편결제|페이 결제|PAY 결제|PG사|앱 결제|온라인 결제')
               AS needs_channel_context,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '상품권.*제외|입점매장.*제외|일부 매장.*제외|특정 상품.*제외|'
               '간편결제.*제외|온라인.*제외') AS needs_exclusion_context,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '일 [0-9]+회|월 (최대 )?[0-9]+회|연 [0-9]+회') AS needs_count_limit,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '최대 [0-9,천만]+원까지|이용금액 최대|할인 대상 금액 최대')
               AS needs_transaction_cap,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '[0-9,천만]+원 (할인|캐시백)|[0-9,]+P 적립|리터당|km당')
               AS needs_reward_calculation,
           MAX(EXISTS (
               SELECT 1 FROM benefit_offers offer
               INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
               INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
               WHERE offer.benefit_id = benefit.benefit_id
                 AND target.match_mode = 'include'
                 AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
                 AND rule_data.rule_effect = 'grant'
                 AND rule_data.reward_value IS NOT NULL
                 AND rule_data.reward_unit IN ('percent', 'KRW', 'point', 'mile')
           )) AS is_ready,
           MAX(EXISTS (
               SELECT 1 FROM benefit_offers offer
               INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
               INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
               WHERE offer.benefit_id = benefit.benefit_id
                 AND target.match_mode = 'include'
                 AND target.target_type = 'merchant'
           )) AS has_merchant_target,
           MAX(EXISTS (
               SELECT 1 FROM benefit_offers offer
               INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
               INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
               WHERE offer.benefit_id = benefit.benefit_id
                 AND target.match_mode = 'include'
           )) AS has_any_target
    FROM cards card
    INNER JOIN issuers issuer ON issuer.issuer_id = card.issuer_id
    INNER JOIN card_content_versions version ON version.card_id = card.card_id
      AND NOT EXISTS (
          SELECT 1 FROM card_content_versions newer
          WHERE newer.card_id = version.card_id
            AND (newer.last_seen_at > version.last_seen_at
                 OR (newer.last_seen_at = version.last_seen_at
                     AND newer.content_version_id > version.content_version_id))
      )
    LEFT JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    WHERE card.gorilla_card_id IS NOT NULL
    GROUP BY card.card_id, card.gorilla_card_id, issuer.issuer_name, version.name
), classified AS (
    SELECT evidence.*,
           CASE
               WHEN is_ready = 1 THEN 'READY'
               WHEN has_offline_monetary_candidate = 0 THEN 'OUT_OF_SCOPE'
               ELSE 'BLOCKED'
           END AS coverage_status,
           CASE
               WHEN is_ready = 1 THEN NULL
               WHEN has_offline_monetary_candidate = 0 THEN
                   CASE WHEN has_out_of_scope_evidence = 1
                        THEN 'NO_SUPPORTED_OFFLINE_BENEFIT'
                        ELSE 'NON_MONETARY_ONLY' END
               WHEN has_merchant_candidate = 1 AND has_merchant_target = 0
                   THEN 'MERCHANT_UNMAPPED'
               WHEN needs_channel_context = 1 THEN 'CHANNEL_CONTEXT_REQUIRED'
               WHEN needs_exclusion_context = 1 THEN 'EXCLUSION_CONTEXT_REQUIRED'
               WHEN needs_count_limit = 1 THEN 'COUNT_LIMIT_UNSUPPORTED'
               WHEN needs_transaction_cap = 1 THEN 'TRANSACTION_CAP_UNSUPPORTED'
               WHEN needs_reward_calculation = 1 THEN 'REWARD_CALCULATION_UNSUPPORTED'
               WHEN has_any_target = 0 THEN 'TARGET_MAPPING_FAILED'
               ELSE 'PARSER_FAILED'
           END AS primary_blocker,
           CONCAT_WS(',',
               IF(has_merchant_candidate = 1 AND has_merchant_target = 0,
                  'TARGET_UNMAPPED_SYMPTOM', NULL),
               IF(needs_channel_context = 1, 'CHANNEL_CONTEXT_REQUIRED', NULL),
               IF(needs_exclusion_context = 1, 'EXCLUSION_CONTEXT_REQUIRED', NULL),
               IF(needs_count_limit = 1, 'COUNT_LIMIT_UNSUPPORTED', NULL),
               IF(needs_transaction_cap = 1, 'TRANSACTION_CAP_UNSUPPORTED', NULL)
           ) AS secondary_blockers
    FROM card_evidence evidence
)
SELECT CASE
           WHEN coverage_status = 'READY' THEN 'READY'
           WHEN coverage_status = 'OUT_OF_SCOPE' THEN 'OUT_OF_SCOPE'
           WHEN primary_blocker IN ('MERCHANT_UNMAPPED', 'TARGET_MAPPING_FAILED')
               THEN 'DATA_MISSING'
           WHEN primary_blocker IN ('CHANNEL_CONTEXT_REQUIRED', 'EXCLUSION_CONTEXT_REQUIRED')
               THEN 'CONDITIONAL'
           ELSE 'UNSUPPORTED'
       END AS card_status,
       COALESCE(primary_blocker, 'READY') AS primary_reason,
       COUNT(*) AS card_count
FROM classified
GROUP BY card_status, COALESCE(primary_blocker, 'READY')
ORDER BY FIELD(card_status, 'READY', 'CONDITIONAL', 'OUT_OF_SCOPE', 'UNSUPPORTED', 'DATA_MISSING'),
         card_count DESC;

-- 카드별 상세: primary reason은 상호 배타적이고 secondary는 증상/추가 조건이다.
WITH card_evidence AS (
    SELECT card.card_id, card.gorilla_card_id, issuer.issuer_name,
           version.name AS card_name,
           MAX(EXISTS (
               SELECT 1 FROM benefit_offers offer
               INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
               INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
               WHERE offer.benefit_id = benefit.benefit_id
                 AND target.match_mode = 'include'
                 AND rule_data.rule_effect = 'grant'
                 AND rule_data.reward_value IS NOT NULL
                 AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
           )) AS is_ready,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '편의점|카페|커피|음식점|외식|마트|백화점|영화|병원|약국|학원|주유|'
               '택시|버스|지하철|대중교통|베이커리|패스트푸드|테마파크|'
               '모든가맹점|국내가맹점|국내외가맹점')
               AS in_scope,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               'GS ?25|지에스25|CU|씨유|스타벅스|STARBUCKS|CGV|씨지브이|롯데시네마|'
               '메가박스|올리브영') AS merchant_candidate,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '간편결제|페이 결제|PG사|앱 결제') AS channel_context,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '상품권.*제외|입점매장.*제외|일부 매장.*제외') AS exclusion_context,
           SUBSTRING_INDEX(GROUP_CONCAT(
               IF(benefit.record_type = 'benefit', benefit.title, NULL)
               ORDER BY benefit.position SEPARATOR ' | '), ' | ', 1) AS recoverable_benefit
    FROM cards card
    INNER JOIN issuers issuer ON issuer.issuer_id = card.issuer_id
    INNER JOIN card_content_versions version ON version.card_id = card.card_id
      AND NOT EXISTS (
          SELECT 1 FROM card_content_versions newer
          WHERE newer.card_id = version.card_id
            AND (newer.last_seen_at > version.last_seen_at
                 OR (newer.last_seen_at = version.last_seen_at
                     AND newer.content_version_id > version.content_version_id))
      )
    LEFT JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    WHERE card.gorilla_card_id IS NOT NULL
    GROUP BY card.card_id, card.gorilla_card_id, issuer.issuer_name, version.name
)
SELECT card_name, issuer_name,
       IF(in_scope = 1, 'IN_SCOPE', 'OUT_OF_SCOPE') AS scope_status,
       CASE
           WHEN is_ready = 1 THEN 'READY'
           WHEN in_scope = 0 THEN 'OUT_OF_SCOPE'
           WHEN merchant_candidate = 1 THEN 'DATA_MISSING'
           WHEN channel_context = 1 OR exclusion_context = 1 THEN 'CONDITIONAL'
           ELSE 'UNSUPPORTED'
       END AS card_status,
       CASE
           WHEN is_ready = 1 THEN 'READY'
           WHEN in_scope = 0 THEN 'NO_SUPPORTED_OFFLINE_BENEFIT'
           WHEN merchant_candidate = 1 THEN 'MERCHANT_UNMAPPED'
           WHEN channel_context = 1 THEN 'CHANNEL_CONTEXT_REQUIRED'
           WHEN exclusion_context = 1 THEN 'EXCLUSION_CONTEXT_REQUIRED'
           ELSE 'TARGET_OR_PARSER_FAILED'
       END AS primary_blocker,
       recoverable_benefit,
       CASE
           WHEN merchant_candidate = 1 THEN 'MERCHANT_ALIAS_OR_FK'
           WHEN channel_context = 1 THEN 'PAYMENT_CHANNEL_CONTEXT'
           WHEN exclusion_context = 1 THEN 'CONDITIONAL_EXCLUSION_POLICY'
           ELSE 'TARGET_AND_REWARD_PARSER'
       END AS required_feature
FROM card_evidence
ORDER BY FIELD(primary_blocker, 'READY'), issuer_name, card_name;

-- Root cause와 파생 symptom의 교집합. merchant 후보인데 merchant target이 없으면
-- TARGET_UNMAPPED는 별도 primary가 아니라 MERCHANT_UNMAPPED의 symptom이다.
WITH failure_flags AS (
    SELECT card.card_id, benefit.benefit_id,
           CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               'GS ?25|지에스25|CU|씨유|스타벅스|STARBUCKS|CGV|씨지브이|롯데시네마|'
               '메가박스|올리브영' AS merchant_candidate,
           NOT EXISTS (
               SELECT 1 FROM benefit_offers offer
               INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
               INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
               WHERE offer.benefit_id = benefit.benefit_id
                 AND target.match_mode = 'include'
           ) AS target_unmapped,
           EXISTS (
               SELECT 1 FROM benefit_offers offer
               INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
               INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
               WHERE offer.benefit_id = benefit.benefit_id
                 AND target.match_mode = 'include'
                 AND target.target_type = 'merchant'
           ) AS has_merchant_target
    FROM cards card
    INNER JOIN card_content_versions version ON version.card_id = card.card_id
      AND NOT EXISTS (
          SELECT 1 FROM card_content_versions newer
          WHERE newer.card_id = version.card_id
            AND (newer.last_seen_at > version.last_seen_at
                 OR (newer.last_seen_at = version.last_seen_at
                     AND newer.content_version_id > version.content_version_id))
      )
    INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    WHERE card.gorilla_card_id IS NOT NULL AND benefit.record_type = 'benefit'
), benefit_overlap AS (
    SELECT card_id, benefit_id, target_unmapped,
           merchant_candidate = 1 AND has_merchant_target = 0 AS merchant_unmapped
    FROM failure_flags
), card_overlap AS (
    SELECT card_id, MAX(target_unmapped) AS target_unmapped,
           MAX(merchant_unmapped) AS merchant_unmapped
    FROM benefit_overlap
    GROUP BY card_id
)
SELECT 'BENEFIT' AS aggregation_level,
       SUM(target_unmapped = 1 AND merchant_unmapped = 0) AS target_only,
       SUM(merchant_unmapped = 1 AND target_unmapped = 0) AS merchant_only,
       SUM(target_unmapped = 1 AND merchant_unmapped = 1) AS target_merchant_intersection
FROM benefit_overlap
UNION ALL
SELECT 'CARD',
       SUM(target_unmapped = 1 AND merchant_unmapped = 0) AS target_only,
       SUM(merchant_unmapped = 1 AND target_unmapped = 0) AS merchant_only,
       SUM(target_unmapped = 1 AND merchant_unmapped = 1) AS target_merchant_intersection
FROM card_overlap;

-- 두 KPI를 함께 노출한다. OUT_OF_SCOPE는 위의 카드 원문 근거로만 제외한다.
WITH coverage AS (
    SELECT card.card_id,
           MAX(EXISTS (
               SELECT 1 FROM benefit_offers offer
               INNER JOIN benefit_rules rule_data ON rule_data.offer_id = offer.offer_id
               INNER JOIN benefit_rule_targets target ON target.rule_id = rule_data.rule_id
               WHERE offer.benefit_id = benefit.benefit_id
                 AND target.match_mode = 'include'
                 AND rule_data.rule_effect = 'grant'
                 AND rule_data.reward_value IS NOT NULL
                 AND offer.reward_type IN ('discount', 'cashback', 'points', 'rebate')
           )) AS is_ready,
           MAX(CONCAT_WS(' ', benefit.detail_text, benefit.summary, benefit.title) REGEXP
               '편의점|카페|커피|음식점|외식|마트|백화점|영화|병원|약국|학원|주유|'
               '택시|버스|지하철|대중교통|베이커리|패스트푸드|테마파크|'
               '모든가맹점|국내가맹점|국내외가맹점')
               AS in_scope
    FROM cards card
    INNER JOIN card_content_versions version ON version.card_id = card.card_id
      AND NOT EXISTS (
          SELECT 1 FROM card_content_versions newer
          WHERE newer.card_id = version.card_id
            AND (newer.last_seen_at > version.last_seen_at
                 OR (newer.last_seen_at = version.last_seen_at
                     AND newer.content_version_id > version.content_version_id))
      )
    LEFT JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    WHERE card.gorilla_card_id IS NOT NULL
    GROUP BY card.card_id
)
SELECT COUNT(*) AS total_cards,
       SUM(is_ready = 1) AS ready_cards,
       SUM(in_scope = 1) AS in_scope_cards,
       SUM(in_scope = 0) AS out_of_scope_cards,
       ROUND(100 * SUM(is_ready = 1) / COUNT(*), 2) AS total_card_coverage_percent,
       ROUND(100 * SUM(is_ready = 1 AND in_scope = 1) / NULLIF(SUM(in_scope = 1), 0), 2)
           AS in_scope_card_coverage_percent
FROM coverage;

-- 원문 merchant 후보 → canonical/alias/FK 매핑 감사.
WITH merchant_dictionary AS (
    SELECT 'GS25' canonical_name, 'GS ?25|지에스25|GS리테일 GS25' source_pattern
    UNION ALL SELECT 'CU', '(^|[^A-Z])CU([^A-Z]|$)|씨유'
    UNION ALL SELECT '스타벅스', '스타벅스|STARBUCKS'
    UNION ALL SELECT 'CGV', 'CGV|씨지브이|CJ CGV'
    UNION ALL SELECT '롯데시네마', '롯데시네마'
    UNION ALL SELECT '메가박스', '메가박스'
    UNION ALL SELECT '올리브영', '올리브영'
), candidates AS (
    SELECT DISTINCT benefit.benefit_id, dictionary.canonical_name
    FROM card_benefits benefit
    INNER JOIN merchant_dictionary dictionary
      ON CONCAT_WS(' ', benefit.title, benefit.summary, benefit.detail_text)
         REGEXP dictionary.source_pattern
    WHERE benefit.record_type = 'benefit'
), resolved AS (
    SELECT candidate.*,
           direct.merchant_id AS direct_merchant_id,
           alias_data.merchant_id AS alias_merchant_id
    FROM candidates candidate
    LEFT JOIN merchants direct ON direct.normalized_name = candidate.canonical_name
    LEFT JOIN merchant_aliases alias_data
      ON alias_data.normalized_alias_name = candidate.canonical_name
)
SELECT COUNT(*) AS merchant_candidates,
       SUM(direct_merchant_id IS NOT NULL) AS direct_matches,
       SUM(direct_merchant_id IS NULL AND alias_merchant_id IS NOT NULL) AS alias_matches,
       COUNT(DISTINCT IF(direct_merchant_id IS NULL AND alias_merchant_id IS NULL,
                         canonical_name, NULL)) AS new_canonical_merchants_needed,
       SUM(direct_merchant_id IS NULL AND alias_merchant_id IS NULL) AS unmapped_candidates
FROM resolved;

-- 거래 context별 영향 카드. affected는 해당 조건이 원문에 있는 카드 수이며,
-- newly_ready는 다른 blocker까지 모두 해소된 뒤 별도로 계산해야 하므로 여기서 합산하지 않는다.
WITH card_context AS (
    SELECT card.card_id,
           MAX(CONCAT_WS(' ', benefit.title, benefit.summary, benefit.detail_text) REGEXP
               '간편결제|페이 결제|PAY 결제|PG사') AS payment_channel,
           MAX(CONCAT_WS(' ', benefit.title, benefit.summary, benefit.detail_text) REGEXP
               '상품권.*제외') AS gift_card_purchase,
           MAX(CONCAT_WS(' ', benefit.title, benefit.summary, benefit.detail_text) REGEXP
               '입점매장.*제외') AS tenant_store,
           MAX(CONCAT_WS(' ', benefit.title, benefit.summary, benefit.detail_text) REGEXP
               '온라인 결제|앱 결제') AS online_context,
           MAX(CONCAT_WS(' ', benefit.title, benefit.summary, benefit.detail_text) REGEXP
               '평일|주말|요일|오전|오후|시부터|시까지') AS expected_payment_at
    FROM cards card
    INNER JOIN card_content_versions version ON version.card_id = card.card_id
    INNER JOIN card_benefits benefit ON benefit.content_version_id = version.content_version_id
    WHERE card.gorilla_card_id IS NOT NULL
    GROUP BY card.card_id
)
SELECT 'paymentChannel/walletProvider' AS context_field, 'FLOW_DEPENDENT' AS inference,
       SUM(payment_channel) AS affected_cards
FROM card_context
UNION ALL SELECT 'isGiftCardPurchase', 'USER_INPUT_UNNATURAL', SUM(gift_card_purchase)
FROM card_context
UNION ALL SELECT 'isTenantStore', 'MERCHANT_METADATA_PREFERRED', SUM(tenant_store)
FROM card_context
UNION ALL SELECT 'isOnline', 'MERCHANT_LOCATION_INFERABLE', SUM(online_context)
FROM card_context
UNION ALL SELECT 'expectedPaymentAt', 'SERVER_AUTOMATIC', SUM(expected_payment_at)
FROM card_context;
