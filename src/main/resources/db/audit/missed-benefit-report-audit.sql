-- 월별 놓친 혜택 리포트 운영 장애 감사 SQL
-- 운영 DB에서 실행하기 전에 아래 두 값만 사례에 맞게 변경한다.
SET @user_card_id = 'c378e8e0-744a-4df0-bd77-73233c364e98';
SET @year_month = '2026-08';

-- 1) 카드·실적 snapshot·실적 구간
SELECT uc.user_card_id, cv.name AS card_name, snapshot.performance_month,
       snapshot.current_spend_amount, tier.tier_number,
       tier.minimum_spend_krw, tier.maximum_spend_krw
FROM user_cards uc
JOIN cards card ON card.card_id = uc.card_id
LEFT JOIN card_content_versions cv ON cv.card_id = card.card_id
LEFT JOIN user_card_performance_snapshots snapshot
  ON snapshot.user_card_id = uc.user_card_id
 AND snapshot.performance_month = @year_month
LEFT JOIN card_performance_tiers tier
  ON tier.content_version_id = cv.content_version_id
 AND tier.minimum_spend_krw <= COALESCE(snapshot.current_spend_amount, 0)
WHERE uc.user_card_id = @user_card_id
ORDER BY tier.tier_number;

-- 2) 카드 혜택·룰·target·월 한도
SELECT offer.offer_name, rule.rule_id, offer.reward_type, rule.reward_unit,
       rule.reward_value, target.target_type, target.target_code,
       target.merchant_category_id, target.merchant_id,
       policy.shared_group_key, policy.limit_unit, limit_tier.limit_value,
       rule.previous_spend_min_krw, rule.transaction_min_krw,
       rule.transaction_max_krw, rule.rule_definition_json
FROM user_cards uc
JOIN card_content_versions cv ON cv.card_id = uc.card_id
JOIN card_benefits benefit ON benefit.content_version_id = cv.content_version_id
JOIN benefit_offers offer ON offer.benefit_id = benefit.benefit_id
JOIN benefit_rules rule ON rule.offer_id = offer.offer_id
LEFT JOIN benefit_rule_targets target ON target.rule_id = rule.rule_id
LEFT JOIN benefit_limit_policies policy ON policy.offer_id = offer.offer_id
LEFT JOIN benefit_limit_tiers limit_tier ON limit_tier.limit_policy_id = policy.limit_policy_id
WHERE uc.user_card_id = @user_card_id
ORDER BY offer.position, rule.position, target.condition_group, limit_tier.position;

-- 3) 해당 월 승인·가맹점·카테고리·취소 상태
SELECT approval.approval_id, approval.approved_at, approval.amount,
       approval.merchant_id, approval.merchant_name, merchant.normalized_name,
       category.category_code, category.category_name,
       approval.approval_status, approval.approval_number
FROM card_payment_approvals approval
LEFT JOIN merchants merchant ON merchant.merchant_id = approval.merchant_id
LEFT JOIN merchant_categories category ON category.merchant_category_id = merchant.merchant_category_id
WHERE approval.user_card_id = @user_card_id
  AND approval.approved_at >= CONCAT(@year_month, '-01')
  AND approval.approved_at < DATE_ADD(CONCAT(@year_month, '-01'), INTERVAL 1 MONTH)
ORDER BY approval.approved_at, approval.approval_id;

-- 4) 승인 합계와 calculation outcome·usage 원장 비교
SELECT COUNT(*) AS approval_count, COALESCE(SUM(amount), 0) AS approval_gross_amount
FROM card_payment_approvals
WHERE user_card_id = @user_card_id
  AND approved_at >= CONCAT(@year_month, '-01')
  AND approved_at < DATE_ADD(CONCAT(@year_month, '-01'), INTERVAL 1 MONTH);

SELECT outcome.rule_id, outcome.approval_id, outcome.outcome_status,
       outcome.reward_unit, outcome.expected_reward_value,
       outcome.applied_reward_value, outcome.missed_reward_value,
       outcome.rejection_reason
FROM user_benefit_calculation_outcomes outcome
WHERE outcome.user_card_id = @user_card_id
  AND outcome.usage_date >= CONCAT(@year_month, '-01')
  AND outcome.usage_date < DATE_ADD(CONCAT(@year_month, '-01'), INTERVAL 1 MONTH)
ORDER BY outcome.usage_date, outcome.rule_id, outcome.approval_id;

SELECT usage.rule_id, usage.approval_id, usage.reward_amount_krw,
       usage.reward_original_value, usage.reward_original_unit, usage.usage_status
FROM user_benefit_usages usage
WHERE usage.user_card_id = @user_card_id
  AND usage.usage_date >= CONCAT(@year_month, '-01')
  AND usage.usage_date < DATE_ADD(CONCAT(@year_month, '-01'), INTERVAL 1 MONTH)
ORDER BY usage.usage_date, usage.rule_id, usage.approval_id;

-- 5) FK·target 코드 불일치 및 상위/삭제 카테고리 참조 검사
SELECT target.target_id, target.rule_id, target.target_type,
       target.target_code, target.merchant_category_id, target.merchant_id,
       category.category_code, merchant.normalized_name
FROM benefit_rule_targets target
LEFT JOIN merchant_categories category
  ON category.merchant_category_id = target.merchant_category_id
LEFT JOIN merchants merchant ON merchant.merchant_id = target.merchant_id
WHERE (target.target_type = 'merchant_category'
       AND (category.merchant_category_id IS NULL
            OR target.target_code <> category.category_code))
   OR (target.target_type = 'merchant'
       AND (merchant.merchant_id IS NULL
            OR target.target_code <> merchant.normalized_name));
