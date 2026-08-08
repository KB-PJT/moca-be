import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const sourceArgument = process.argv[2];
if (!sourceArgument) {
    throw new Error("사용법: node scripts/generate-card-gorilla-seed.mjs <card-gorilla-json>");
}

const sourcePath = path.resolve(sourceArgument);
const sourceBuffer = fs.readFileSync(sourcePath);
const cards = JSON.parse(sourceBuffer.toString("utf8"));
if (!Array.isArray(cards)) {
    throw new Error("카드고릴라 원본의 최상위 값은 배열이어야 합니다.");
}

const namespace = uuidBytes("d61d9b26-f9d8-5fde-8f1e-35f51a273970");
const gorillaCategoryCodes = new Map([
    [4, "EDUCATION"], [47, "EDUCATION"], [48, "EDUCATION"],
    [5, "TRANSPORTATION"], [50, "TRANSPORTATION"], [51, "TRANSPORTATION"],
    [52, "TRANSPORTATION"], [53, "TRANSPORTATION"], [8, "LEISURE"], [70, "LEISURE"], [71, "LEISURE"],
    [13, "HEALTHCARE"], [14, "BEAUTY"], [90, "BEAUTY"], [17, "PET"],
    [21, "FUEL"], [122, "FUEL"], [22, "CAFE"], [125, "BAKERY"], [127, "CAFE"],
    [23, "TELECOM"], [129, "TELECOM"], [35, "UTILITY_BILL"], [55, "INSURANCE"], [117, "INSURANCE"],
    [75, "MART"], [76, "TRADITIONAL_MARKET"], [77, "CONVENIENCE_STORE"],
    [96, "DUTY_FREE"], [97, "DEPARTMENT_STORE"], [99, "ONLINE_SHOPPING"],
    [112, "CULTURE"], [113, "BOOKS"], [114, "MUSIC"], [115, "MOVIE"], [185, "OTT"],
    [24, "RESTAURANT"], [118, "AUTOMOTIVE"], [120, "AUTOMOTIVE"], [133, "RESTAURANT"],
    [135, "FAMILY_RESTAURANT"], [136, "FAST_FOOD"], [137, "RESTAURANT"], [139, "DELIVERY"],
    [141, "AIRLINE"], [145, "AIRLINE"]
]);
const issuers = [...new Set(cards.map((card) => card.issuer))].sort();
const issuerRows = issuers.map((issuer, index) => [
    uuidV5(`issuer:${issuer}`),
    `CG${String(index + 1).padStart(8, "0")}`,
    issuer,
    seedDate(cards),
    seedDate(cards),
]);

// 카드고릴라의 혜택 분류는 화면 분류이므로, 승인내역 계산에 사용할 수 있는 가맹점 카테고리만
// 명시적으로 승격한다. 간편결제·포인트·유의사항처럼 가맹점 조건이 아닌 분류는 의도적으로 제외한다.
const categoryDefinitions = [
    ["EDUCATION", "교육"], ["TRANSPORTATION", "교통"], ["LEISURE", "레저"],
    ["MART", "마트"], ["TRADITIONAL_MARKET", "전통시장"], ["CONVENIENCE_STORE", "편의점"],
    ["HEALTHCARE", "병원·약국"], ["BEAUTY", "뷰티"], ["SHOPPING", "쇼핑"],
    ["DUTY_FREE", "면세점"], ["DEPARTMENT_STORE", "백화점"], ["ONLINE_SHOPPING", "온라인쇼핑"],
    ["PET", "반려동물"], ["TRAVEL", "여행"], ["OTT", "OTT"], ["CULTURE", "문화"],
    ["BOOKS", "도서"], ["MUSIC", "음원"], ["MOVIE", "영화"], ["AUTOMOTIVE", "자동차"],
    ["FUEL", "주유"], ["CAFE", "카페"], ["BAKERY", "베이커리"], ["TELECOM", "통신"],
    ["RESTAURANT", "음식점"], ["FAMILY_RESTAURANT", "패밀리레스토랑"], ["FAST_FOOD", "패스트푸드"],
    ["DELIVERY", "배달"], ["UTILITY_BILL", "공과금"], ["INSURANCE", "보험"], ["AIRLINE", "항공"],
];
const categoryRows = categoryDefinitions.map(([code, name], index) => [
    uuidV5(`merchant-category:${code}`), code, name, index + 1, seedDate(cards), seedDate(cards),
]);

const cardRows = [];
const contentRows = [];
const annualFeeRows = [];
const performanceTierRows = [];
const benefitRows = [];
const offerRows = [];
const ruleRows = [];
const targetRows = [];
const limitPolicyRows = [];
const limitTierRows = [];
const scheduleRows = [];
const skippedBenefitReasons = new Map();

for (const card of cards) {
    validateCard(card);
    const cardId = uuidV5(`card-gorilla-card:${card.card_id}`);
    const contentHash = sha256(JSON.stringify(contentPayload(card)));
    const contentVersionId = uuidV5(`card-content:${card.card_id}:${contentHash}`);
    const seenAt = mysqlDateTime(card.scraped_at);

    cardRows.push([
        cardId,
        String(card.card_id),
        issuerLookup(card.issuer),
        card.card_type,
        seenAt,
        seenAt,
    ]);
    contentRows.push([
        contentVersionId,
        cardLookup(card.card_id),
        contentHash,
        card.name,
        card.annual_fee,
        card.annual_fee_detail,
        card.previous_month_spend,
        Boolean(card.discontinued),
        null,
        card.event_title,
        card.event_detail_text,
        card.event_detail_html,
        card.image_url,
        card.source_url,
        seenAt,
        seenAt,
    ]);

    annualFeeOptions(card).forEach((option, index) => annualFeeRows.push([
        uuidV5(`annual-fee:${contentVersionId}:${option.usageRegion}:${option.brand ?? "NONE"}:${option.amount}`),
        contentLookup(card.card_id, contentHash),
        index + 1,
        option.usageRegion,
        option.brand,
        option.amount,
        seenAt,
        seenAt,
    ]));

    // 전월 실적 구간은 개별 혜택이 아니라 카드 콘텐츠 전체의 공통 기준이다. 원문에
    // 명시된 구간의 시작 금액만 추출하고, 다음 시작 금액 직전까지를 포함 상한으로 둔다.
    performanceTiers(card).forEach((tier) => performanceTierRows.push([
        uuidV5(`performance-tier:${contentVersionId}:${tier.number}:${tier.minimum}:${tier.maximum ?? "OPEN"}`),
        contentLookup(card.card_id, contentHash),
        tier.number,
        tier.minimum,
        tier.maximum,
        seenAt,
        seenAt,
    ]));

    card.benefits.forEach((benefit, index) => {
        const benefitId = uuidV5(`benefit:${contentVersionId}:${index + 1}:${sha256(JSON.stringify(benefit))}`);
        benefitRows.push([
        benefitId,
        contentLookup(card.card_id, contentHash),
        index + 1,
        recordType(benefit.title),
        benefit.title,
        benefit.description,
        benefit.detail_text,
        benefit.detail_html,
        seenAt,
        seenAt,
        ]);
        const parsed = parseCalculableBenefit(benefit);
        if (parsed.value) {
            addStructuredBenefitRows(benefitId, parsed, seenAt);
        } else if (recordType(benefit.title) === "benefit") {
            addInformationOnlyOffer(benefitId, benefit.title, seenAt);
            skippedBenefitReasons.set(parsed.reason, (skippedBenefitReasons.get(parsed.reason) ?? 0) + 1);
        }
    });
}

assertUnique(cardRows, "card_id");
assertUnique(contentRows, "content_version_id");
assertUnique(annualFeeRows, "fee_option_id");
assertUnique(performanceTierRows, "performance_tier_id");
assertUnique(benefitRows, "benefit_id");

const output = [];
output.push("-- 이 파일은 scripts/generate-card-gorilla-seed.mjs로 생성한다.");
output.push(`-- source: ${path.basename(sourcePath)}`);
output.push(`-- source_sha256: ${sha256(sourceBuffer)}`);
output.push(`-- cards: ${cardRows.length}, benefits: ${benefitRows.length}, annual_fee_options: ${annualFeeRows.length}, performance_tiers: ${performanceTierRows.length}`);
output.push(`-- structured: offers=${offerRows.length}, rules=${ruleRows.length}, targets=${targetRows.length}, limit_policies=${limitPolicyRows.length}, limit_tiers=${limitTierRows.length}, schedules=${scheduleRows.length}`);
for (const [reason, count] of [...skippedBenefitReasons.entries()].sort()) {
    output.push(`-- information_only (${reason}): ${count}`);
}
output.push("-- CG로 시작하는 기관코드는 카드고릴라 전용 임시값이며 CODEF 연동 전에 실제 기관코드로 갱신한다.");
output.push("");
output.push("SET @MOCA_OLD_SQL_MODE = @@SESSION.sql_mode;");
output.push("SET SESSION sql_mode = REPLACE(@@SESSION.sql_mode, 'NO_BACKSLASH_ESCAPES', '');");
output.push("SET NAMES utf8mb4;");
output.push("START TRANSACTION;");
output.push("");
output.push(insertSql(
    "merchant_categories",
    ["merchant_category_id", "category_code", "category_name", "display_order", "created_at", "updated_at"],
    categoryRows,
    ["category_name = VALUES(category_name)", "display_order = VALUES(display_order)", "updated_at = VALUES(updated_at)"],
));
output.push("");
output.push(insertSql(
    "issuers",
    ["issuer_id", "institution_code", "issuer_name", "created_at", "updated_at"],
    issuerRows,
    ["issuer_name = VALUES(issuer_name)", "updated_at = VALUES(updated_at)"],
));
output.push("UPDATE cards c\n"
    + "INNER JOIN card_content_versions cv ON cv.card_id = c.card_id\n"
    + "SET c.gorilla_card_id = SUBSTRING_INDEX(cv.source_url, '/', -1)\n"
    + "WHERE c.gorilla_card_id IS NULL\n"
    + "  AND cv.source_url LIKE 'https://www.card-gorilla.com/card/detail/%';");
output.push(insertSql(
    "cards",
    ["card_id", "gorilla_card_id", "issuer_id", "card_type", "first_seen_at", "last_seen_at"],
    cardRows,
    [
        "issuer_id = VALUES(issuer_id)",
        "card_type = VALUES(card_type)",
        "first_seen_at = LEAST(first_seen_at, VALUES(first_seen_at))",
        "last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at))",
    ],
));
output.push(insertSql(
    "card_content_versions",
    [
        "content_version_id", "card_id", "content_sha256", "name", "annual_fee_summary",
        "annual_fee_detail", "representative_spend", "discontinued", "main_benefits", "event_title",
        "event_detail_text", "event_detail_html", "image_url", "source_url", "first_seen_at", "last_seen_at",
    ],
    contentRows,
    ["last_seen_at = GREATEST(last_seen_at, VALUES(last_seen_at))"],
    25,
));
output.push(insertSql(
    "card_annual_fee_options",
    [
        "fee_option_id", "content_version_id", "position", "usage_region", "brand", "annual_fee_krw",
        "created_at", "updated_at",
    ],
    annualFeeRows,
    [
        "position = VALUES(position)",
        "annual_fee_krw = VALUES(annual_fee_krw)",
        "updated_at = VALUES(updated_at)",
    ],
));
if (performanceTierRows.length > 0) {
    output.push(insertSql(
        "card_performance_tiers",
        [
            "performance_tier_id", "content_version_id", "tier_number", "minimum_spend_krw",
            "maximum_spend_krw", "created_at", "updated_at",
        ],
        performanceTierRows,
        [
            "minimum_spend_krw = VALUES(minimum_spend_krw)",
            "maximum_spend_krw = VALUES(maximum_spend_krw)",
            "updated_at = VALUES(updated_at)",
        ],
    ));
}
output.push(insertSql(
    "card_benefits",
    [
        "benefit_id", "content_version_id", "position", "record_type", "title", "summary", "detail_text",
        "detail_html", "created_at", "updated_at",
    ],
    benefitRows,
    [
        "record_type = VALUES(record_type)",
        "title = VALUES(title)",
        "summary = VALUES(summary)",
        "detail_text = VALUES(detail_text)",
        "detail_html = VALUES(detail_html)",
        "updated_at = VALUES(updated_at)",
    ],
    25,
));
// 혜택 원문(card_benefits)을 먼저 넣어야 계산 테이블의 FK가 항상 유효하다.
if (offerRows.length > 0) {
    output.push(insertSql("benefit_offers", ["offer_id", "benefit_id", "reward_program_id", "offer_name", "position", "priority", "exclusive_group_key", "reward_type", "value_type", "value_unit", "calculation_mode", "calculation_basis", "stacking_mode", "reward_timing", "valuation_scope", "valuation_method", "reference_value_krw", "reference_value_unit", "valid_from", "valid_to", "created_at", "updated_at"], offerRows, ["offer_name = VALUES(offer_name)", "updated_at = VALUES(updated_at)"]));
}
if (ruleRows.length > 0) {
    output.push(insertSql("benefit_rules", ["rule_id", "offer_id", "position", "priority", "rule_name", "rule_effect", "stacking_mode", "reward_value", "reward_unit", "reward_basis_amount", "reward_basis_unit", "previous_spend_min_krw", "current_spend_min_krw", "transaction_min_krw", "transaction_max_krw", "rounding_type", "rounding_unit", "valid_from", "valid_to", "created_at", "updated_at"], ruleRows, ["reward_value = VALUES(reward_value)", "previous_spend_min_krw = VALUES(previous_spend_min_krw)", "transaction_min_krw = VALUES(transaction_min_krw)", "transaction_max_krw = VALUES(transaction_max_krw)", "updated_at = VALUES(updated_at)"]));
    output.push(insertSql("benefit_rule_targets", ["target_id", "rule_id", "condition_group", "match_mode", "target_type", "target_code", "target_name", "created_at", "updated_at"], targetRows, ["target_name = VALUES(target_name)", "updated_at = VALUES(updated_at)"]));
}
if (limitPolicyRows.length > 0) {
    output.push(insertSql("benefit_limit_policies", ["limit_policy_id", "offer_id", "policy_name", "limit_period", "limit_type", "limit_unit", "shared_group_key", "valid_from", "valid_to", "created_at", "updated_at"], limitPolicyRows, ["policy_name = VALUES(policy_name)", "updated_at = VALUES(updated_at)"]));
    output.push(insertSql("benefit_limit_tiers", ["limit_tier_id", "limit_policy_id", "position", "limit_value", "previous_spend_min_krw", "current_spend_min_krw", "updated_at"], limitTierRows.map((row) => [...row.slice(0, -2), row.at(-1)]), ["limit_value = VALUES(limit_value)", "previous_spend_min_krw = VALUES(previous_spend_min_krw)", "updated_at = VALUES(updated_at)"]));
}
if (scheduleRows.length > 0) {
    output.push(insertSql("benefit_rule_schedules", ["schedule_id", "rule_id", "months_json", "days_of_month_json", "days_of_week_json", "start_time", "end_time", "created_at", "updated_at"], scheduleRows, ["months_json = VALUES(months_json)", "days_of_month_json = VALUES(days_of_month_json)", "days_of_week_json = VALUES(days_of_week_json)", "start_time = VALUES(start_time)", "end_time = VALUES(end_time)", "updated_at = VALUES(updated_at)"]));
}
output.push("COMMIT;");
output.push("SET SESSION sql_mode = @MOCA_OLD_SQL_MODE;");
output.push("");

const outputPath = path.join(
    process.cwd(),
    "src/main/resources/db/seed/card_gorilla_without_summary_benefits.sql",
);
fs.mkdirSync(path.dirname(outputPath), {recursive: true});
fs.writeFileSync(outputPath, output.join("\n"));

function insertSql(table, columns, rows, updates, batchSize = 100) {
    const statements = [];
    for (let offset = 0; offset < rows.length; offset += batchSize) {
        const values = rows.slice(offset, offset + batchSize)
            .map((row) => `    (${row.map(sqlValue).join(", ")})`)
            .join(",\n");
        statements.push(
            `INSERT INTO ${table} (${columns.join(", ")}) VALUES\n${values}\n`
            + `ON DUPLICATE KEY UPDATE\n    ${updates.join(",\n    ")};`,
        );
    }
    return statements.join("\n\n");
}

function sqlValue(value) {
    if (value === null || value === undefined) {
        return "NULL";
    }
    if (typeof value === "boolean") {
        return value ? "TRUE" : "FALSE";
    }
    if (typeof value === "number") {
        return String(value);
    }
    if (typeof value === "object" && value.sql) {
        return value.sql;
    }
    return `'${String(value).replaceAll("\\", "\\\\").replaceAll("'", "''").replaceAll("\0", "\\0")}'`;
}

function issuerLookup(issuer) {
    return {sql: `(SELECT issuer_id FROM issuers WHERE issuer_name = ${sqlValue(issuer)})`};
}

function cardLookup(gorillaCardId) {
    return {sql: `(SELECT card_id FROM cards WHERE gorilla_card_id = ${sqlValue(String(gorillaCardId))})`};
}

function contentLookup(gorillaCardId, contentHash) {
    return {
        sql: "(SELECT cv.content_version_id FROM card_content_versions cv "
            + "INNER JOIN cards c ON c.card_id = cv.card_id "
            + `WHERE c.gorilla_card_id = ${sqlValue(String(gorillaCardId))} `
            + `AND cv.content_sha256 = ${sqlValue(contentHash)})`,
    };
}

function annualFeeOptions(card) {
    const options = [];
    const pattern = /(국내전용|해외겸용)\s*\[([^\]]+)](?:원)?/g;
    for (const match of card.annual_fee.matchAll(pattern)) {
        const usageRegion = match[1] === "국내전용" ? "domestic" : "overseas";
        const amount = amountValue(match[2]);
        const brands = usageRegion === "domestic" ? ["LOCAL"] : card.brands;
        (brands.length > 0 ? brands : [null]).forEach((brand) => options.push({
            usageRegion,
            brand,
            amount,
        }));
    }
    if (options.length === 0 && card.annual_fee.trim() === "없음") {
        options.push({usageRegion: "domestic", brand: "LOCAL", amount: 0});
    }
    if (options.length === 0) {
        throw new Error(`연회비를 구조화할 수 없습니다: ${card.card_id} ${card.annual_fee}`);
    }
    return options;
}

function performanceTiers(card) {
    const text = card.benefits
        .map((benefit) => `${benefit.description ?? ""}\n${benefit.detail_text ?? ""}`)
        .join("\n")
        .replaceAll("\u00a0", " ")
        .replaceAll("\r", "");

    const thresholds = new Set();
    // "전월실적 30만원~50만원", "전월 이용금액 40만원 이상"처럼 실적 문맥이
    // 바로 붙은 표기는 가장 신뢰할 수 있는 소스다.
    for (const match of text.matchAll(/전월\s*(?:이용\s*)?(?:금액|실적)\s*(\d[\d,]*(?:\s*[만천백]\s*)?(?:\s*\d[\d,]*\s*[천백])?)\s*원?\s*(?:이상|초과|~|∼|-)/g)) {
        const amount = amountKrw(match[1]);
        if (amount !== null) thresholds.add(amount);
    }
    // "1구간(30만원 이상)"처럼 전월 실적 표의 행 제목에서만 보조 추출한다.
    // 일반적인 연간 이용 조건과 섞이지 않도록 반드시 N구간 표기를 요구한다.
    for (const match of text.matchAll(/\d+\s*구간\s*\(\s*(\d[\d,]*(?:\s*[만천백]\s*)?(?:\s*\d[\d,]*\s*[천백])?)\s*원?\s*(?:이상|초과)/g)) {
        const amount = amountKrw(match[1]);
        if (amount !== null) thresholds.add(amount);
    }
    // 제목이 "전월 이용금액대별"인 표에는 금액 행이 한 줄로 붙어 있는 경우가 많다.
    // 다음 240자 안의 "N만원 이상"만 읽어 카드의 실적 구간으로 승격한다.
    for (const marker of text.matchAll(/전월\s*(?:이용\s*)?(?:금액|실적)(?:대별|별)?[^\n]{0,240}/g)) {
        for (const match of marker[0].matchAll(/(\d[\d,]*(?:\s*[만천백]\s*)?(?:\s*\d[\d,]*\s*[천백])?)\s*원?\s*(?:이상|초과)/g)) {
            const amount = amountKrw(match[1]);
            if (amount !== null) thresholds.add(amount);
        }
    }

    const minimums = [...thresholds].filter((amount) => amount >= 0).sort((left, right) => left - right);
    return minimums.map((minimum, index) => ({
        number: index + 1,
        minimum,
        maximum: index + 1 < minimums.length ? minimums[index + 1] - 1 : null,
    }));
}

function amountValue(value) {
    const normalized = value.replaceAll(",", "").replaceAll("원", "").trim();
    if (normalized === "없음") {
        return 0;
    }
    if (!/^\d+$/.test(normalized)) {
        throw new Error(`연회비 금액이 올바르지 않습니다: ${value}`);
    }
    return Number(normalized);
}

function recordType(title) {
    if (title === "유의사항") {
        return "notice";
    }
    if (title.includes("제외")) {
        return "exclusion";
    }
    return "benefit";
}

function parseCalculableBenefit(benefit) {
    if (recordType(benefit.title) !== "benefit") return {value: null, reason: "non_benefit_record"};
    const text = normalizeBenefitText(benefit);
    // 원문이 이 조건들을 요구하면 현재 승인 데이터만으로 충족 여부를 판정할 수 없다.
    if (/(택\s*1|선택형|간편결제|자동납부|무이자|해외|신규\s*발급|전전월|상품권|선불카드|기프트카드)/i.test(text)) {
        return {value: null, reason: "unsupported_eligibility_condition"};
    }
    if (/(?:통합|공유)\s*(?:월간?|할인|적립|캐시백)?\s*한도|서비스\s*영역/i.test(text)) {
        return {value: null, reason: "shared_limit"};
    }
    if (/(?:평일|주말|공휴일|요일별)/.test(text)) {
        return {value: null, reason: "weekday_or_holiday_condition"};
    }
    if (/(?:앱|APP|온라인|직접\s*접속|정기결제|자동\s*결제)/i.test(text)) {
        return {value: null, reason: "unavailable_channel_condition"};
    }
    if (/(?:제외\s*(?:됩니다|대상|거래|항목)|할인\s*제외|적립\s*제외)/.test(text)) {
        return {value: null, reason: "unstructured_exclusion"};
    }

    const categoryCode = gorillaCategoryCodes.get(Number(benefit.category_id));
    const allMerchants = /(?:국내\s*)?(?:모든|전)\s*가맹점|국내가맹점/.test(text);
    if (!categoryCode && !allMerchants) return {value: null, reason: "unmapped_target"};

    const percentageRewards = uniqueMatches(text, /(\d+(?:\.\d+)?)\s*%\s*(할인|캐시백|적립)/g,
        (match) => `${match[1]}:${match[2]}`);
    const mileageRewards = uniqueMatches(text, /(?:결제\s*)?(\d[\d,]*)\s*원(?:당|마다)\s*(\d+(?:\.\d+)?)\s*마일(?:리지)?\s*(?:적립)?/g,
        (match) => `${match[1]}:${match[2]}`);
    const fixedRewards = uniqueMatches(text,
        /(?:1회|건당|결제\s*(?:시|당)|이용\s*(?:시|당))\s*([\d,]+(?:\s*[만천백]\s*)?(?:\s*[\d,]+\s*[천백])?)\s*원\s*(할인|캐시백)/g,
        (match) => `${amountKrw(match[1])}:${match[2]}`);
    const formulaCount = percentageRewards.length + mileageRewards.length + fixedRewards.length;
    if (formulaCount !== 1) {
        return {value: null, reason: formulaCount === 0 ? "no_single_formula" : "multiple_formulae"};
    }

    const spends = parseSinglePreviousSpend(text);
    if (spends === undefined) return {value: null, reason: "multiple_spend_tiers"};
    const limit = parseSingleMonthlyLimit(text);
    if (limit === undefined) return {value: null, reason: "multiple_or_ambiguous_limit"};
    const transactionBounds = parseTransactionBounds(text);
    if (transactionBounds === undefined) return {value: null, reason: "multiple_transaction_bounds"};
    const schedule = parseDailyTimeRange(text);
    if (schedule === undefined) return {value: null, reason: "multiple_time_ranges"};

    let formula;
    if (percentageRewards.length === 1) {
        const [, value, word] = percentageRewards[0];
        formula = {
            rewardValue: Number(value), rewardUnit: "percent", valueType: "percentage", calculationBasis: "transaction_amount",
            rewardType: word === "적립" ? "points" : word === "캐시백" ? "cashback" : "discount",
            valueUnit: "percent", rewardTiming: word === "적립" ? "point_accrual" : word === "캐시백" ? "cashback" : "statement",
        };
    } else if (mileageRewards.length === 1) {
        const [, basis, miles] = mileageRewards[0];
        formula = {
            rewardValue: Number(miles), rewardUnit: "mile", valueType: "unit_per_amount", calculationBasis: "transaction_amount",
            rewardType: "points", valueUnit: "mile", rewardTiming: "point_accrual",
            rewardBasisAmount: Number(basis.replaceAll(",", "")), rewardBasisUnit: "KRW",
        };
    } else {
        const [, rawAmount, word] = fixedRewards[0];
        formula = {
            rewardValue: amountKrw(rawAmount), rewardUnit: "KRW", valueType: "fixed_amount", calculationBasis: "transaction_amount",
            rewardType: word === "캐시백" ? "cashback" : "discount", valueUnit: "KRW",
            rewardTiming: word === "캐시백" ? "cashback" : "statement",
        };
    }
    return {value: {
        ...formula,
        targetType: allMerchants ? "all_merchants" : "merchant_category",
        targetCode: allMerchants ? "ALL_DOMESTIC_MERCHANTS" : categoryCode,
        previousSpend: spends ?? null,
        limit: limit?.value ?? null,
        limitUnit: limit?.unit ?? null,
        transactionMin: transactionBounds?.min ?? null,
        transactionMax: transactionBounds?.max ?? null,
        schedule,
    }};
}

function addStructuredBenefitRows(benefitId, parsedResult, seenAt) {
    const parsed = parsedResult.value;
    const offerId = uuidV5(`offer:${benefitId}:${parsed.rewardType}:${parsed.rewardValue}:${parsed.rewardUnit}`);
    const ruleId = uuidV5(`rule:${offerId}:1`);
    offerRows.push([offerId, benefitId, null, "자동 구조화 혜택", 1, 0, null, parsed.rewardType, parsed.valueType, parsed.valueUnit, "flat", parsed.calculationBasis, "standalone", parsed.rewardTiming, "transaction", "direct", null, null, null, null, seenAt, seenAt]);
    ruleRows.push([ruleId, offerId, 1, 0, null, "grant", "standalone", parsed.rewardValue, parsed.rewardUnit, parsed.rewardBasisAmount ?? null, parsed.rewardBasisUnit ?? null, parsed.previousSpend, null, parsed.transactionMin, parsed.transactionMax, "floor", 1, null, null, seenAt, seenAt]);
    targetRows.push([uuidV5(`target:${ruleId}:${parsed.targetType}:${parsed.targetCode}`), ruleId, 1, "include", parsed.targetType, parsed.targetCode, null, seenAt, seenAt]);
    if (parsed.limit !== null) {
        const policyId = uuidV5(`limit:${offerId}:monthly:${parsed.limit}:${parsed.limitUnit}`);
        limitPolicyRows.push([policyId, offerId, "자동 구조화 월 한도", "monthly", "reward_amount", parsed.limitUnit, null, null, null, seenAt, seenAt]);
        limitTierRows.push([uuidV5(`limit-tier:${policyId}:1`), policyId, 1, parsed.limit, parsed.previousSpend, null, seenAt, seenAt]);
    }
    if (parsed.schedule) {
        scheduleRows.push([uuidV5(`schedule:${ruleId}`), ruleId, null, null, null, parsed.schedule.startTime, parsed.schedule.endTime, seenAt, seenAt]);
    }
}

function addInformationOnlyOffer(benefitId, title, seenAt) {
    const offerId = uuidV5(`offer:${benefitId}:information-only`);
    // 원문은 보존하되 계산 가능 여부가 확정되지 않은 혜택은 non_monetary/other로 저장한다.
    // 이를 all_merchants 룰로 바꾸지 않아야 승인건에 과대 적용되지 않는다.
    offerRows.push([offerId, benefitId, null, title, 1, 0, null, "other", "other", null,
        "not_applicable", "none", "not_stackable", null, "non_monetary", "not_valued",
        null, null, null, null, seenAt, seenAt]);
}

function normalizeBenefitText(benefit) {
    return `${benefit.title ?? ""}\n${benefit.description ?? ""}\n${benefit.detail_text ?? ""}`
        .replaceAll("\u00a0", " ")
        .replaceAll("\r", "")
        .replace(/[：]/g, ":")
        .replace(/\s+/g, " ")
        .trim();
}

function uniqueMatches(text, pattern, key) {
    return [...new Map([...text.matchAll(pattern)].map((match) => [key(match), match])).values()];
}

function parseSinglePreviousSpend(text) {
    const values = uniqueMatches(text, /전월\s*(?:이용(?:금액|실적)?|실적)\s*(\d[\d,]*(?:\s*만)?\s*원)\s*이상/g,
        (match) => amountKrw(match[1]));
    if (values.length > 1) return undefined;
    return values.length === 1 ? amountKrw(values[0][1]) : null;
}

// "월 최대 5천원", "월 할인 한도 5,000원"처럼 한도와 단위가 한 번만 명시된 경우만 채택한다.
// 실적 구간별/통합 한도는 혜택 단위로 안전하게 귀속할 수 없으므로 information_only로 남긴다.
function parseSingleMonthlyLimit(text) {
    const matches = uniqueMatches(text,
        /월\s*(?:최대\s*)?([\d,]+(?:\s*[만천백]\s*)?(?:\s*[\d,]+\s*[천백])?)\s*(원|포인트|p|마일)(?:\s*(?:까지|한도|할인|적립|캐시백))?/g,
        (match) => `${amountKrw(match[1])}:${match[2]}`);
    if (matches.length > 1) return undefined;
    if (matches.length === 0) return null;
    const [, amount, rawUnit] = matches[0];
    const value = amountKrw(amount);
    if (value === null) return undefined;
    return {value, unit: rawUnit === "원" ? "KRW" : rawUnit === "마일" ? "mile" : "point"};
}

function parseTransactionBounds(text) {
    const maxMatches = uniqueMatches(text,
        /(?:1회|건당)\s*(?:이용\s*)?(?:금액\s*)?([\d,]+(?:\s*[만천백]\s*)?(?:\s*[\d,]+\s*[천백])?)\s*원?\s*(?:까지|이하)/g,
        (match) => amountKrw(match[1]));
    const minMatches = uniqueMatches(text,
        /(?:1회|건당)\s*(?:이용\s*)?(?:금액\s*)?([\d,]+(?:\s*[만천백]\s*)?(?:\s*[\d,]+\s*[천백])?)\s*원?\s*이상/g,
        (match) => amountKrw(match[1]));
    if (maxMatches.length > 1 || minMatches.length > 1) return undefined;
    const min = minMatches.length ? amountKrw(minMatches[0][1]) : null;
    const max = maxMatches.length ? amountKrw(maxMatches[0][1]) : null;
    if (min !== null && max !== null && min > max) return undefined;
    return min === null && max === null ? null : {min, max};
}

// 날짜 조건 없이 매일 적용되는 단일 시각 범위만 구조화한다. 자정을 넘는 경우는 evaluator가
// 해석해야 하므로 두 시간은 원문 그대로 보존한다.
function parseDailyTimeRange(text) {
    const ranges = uniqueMatches(text,
        /(오전|오후)\s*(\d{1,2})(?:\s*시(?:\s*(\d{1,2})\s*분?)?)?\s*(?:부터|~|∼|–|-)\s*(오전|오후)\s*(\d{1,2})(?:\s*시(?:\s*(\d{1,2})\s*분?)?)?/g,
        (match) => `${toTime(match[1], match[2], match[3])}:${toTime(match[4], match[5], match[6])}`);
    if (ranges.length > 1) return undefined;
    if (ranges.length === 0) return null;
    return {startTime: toTime(ranges[0][1], ranges[0][2], ranges[0][3]), endTime: toTime(ranges[0][4], ranges[0][5], ranges[0][6])};
}

function toTime(period, rawHour, rawMinute) {
    let hour = Number(rawHour);
    if (hour === 12) hour = 0;
    if (period === "오후") hour += 12;
    return `${String(hour).padStart(2, "0")}:${String(rawMinute ?? 0).padStart(2, "0")}:00`;
}

function amountKrw(raw) {
    const value = String(raw).replaceAll(",", "").replaceAll("원", "").replace(/\s+/g, "");
    if (/^\d+$/.test(value)) return Number(value);
    if (!/^(?:\d*만)?(?:\d*천)?(?:\d*백)?$/.test(value)) return null;
    let total = 0;
    for (const [unit, multiplier] of [["만", 10000], ["천", 1000], ["백", 100]]) {
        const match = value.match(new RegExp(`(\\d*)${unit}`));
        if (match) total += Number(match[1] || 1) * multiplier;
    }
    return total > 0 ? total : null;
}

function contentPayload(card) {
    return {
        name: card.name,
        cardType: card.card_type,
        annualFee: card.annual_fee,
        annualFeeDetail: card.annual_fee_detail,
        representativeSpend: card.previous_month_spend,
        discontinued: card.discontinued,
        benefits: card.benefits,
        eventTitle: card.event_title,
        eventDetailText: card.event_detail_text,
        eventDetailHtml: card.event_detail_html,
        imageUrl: card.image_url,
        sourceUrl: card.source_url,
    };
}

function validateCard(card) {
    if (!card.card_id || !card.issuer || !card.name || !Array.isArray(card.benefits)) {
        throw new Error("필수 카드 필드가 누락됐습니다.");
    }
    if (!new Set(["credit", "check"]).has(card.card_type)) {
        throw new Error(`지원하지 않는 카드 종류입니다: ${card.card_type}`);
    }
}

function assertUnique(rows, idName) {
    const ids = new Set();
    for (const row of rows) {
        if (!ids.add(row[0])) {
            throw new Error(`중복 ${idName}가 생성됐습니다: ${row[0]}`);
        }
    }
}

function mysqlDateTime(value) {
    const match = String(value).match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?/);
    if (!match) {
        throw new Error(`수집 시각 형식이 올바르지 않습니다: ${value}`);
    }
    return `${match[1]} ${match[2]}.${(match[3] ?? "").padEnd(6, "0")}`;
}

function seedDate(sourceCards) {
    return sourceCards.map((card) => mysqlDateTime(card.scraped_at)).sort()[0];
}

function sha256(value) {
    return crypto.createHash("sha256").update(value).digest("hex");
}

function uuidV5(value) {
    const hash = crypto.createHash("sha1").update(namespace).update(value).digest().subarray(0, 16);
    hash[6] = (hash[6] & 0x0f) | 0x50;
    hash[8] = (hash[8] & 0x3f) | 0x80;
    const hex = hash.toString("hex");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function uuidBytes(value) {
    return Buffer.from(value.replaceAll("-", ""), "hex");
}
