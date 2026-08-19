# MOCA 카드 혜택 JSON Rule Evaluator 운영 가이드

## 1. 문서 목적

이 문서는 CODEF 승인내역과 MOCA 내부 원장으로 카드 혜택을 계산하는 JSON Rule Evaluator의 설계와
운영 경계를 설명한다. 새로운 카드 혜택을 구조화하거나 계산 오류를 조사할 때 이 문서를 기준으로 한다.

핵심 원칙은 다음과 같다.

- 카드 약관을 임의의 Java `if/else` 코드로 계속 추가하지 않는다.
- 임의 스크립트를 실행하지 않고 허용 목록 기반 JSON DSL만 사용한다.
- 확인할 수 없는 조건을 충족한 것으로 추정하지 않는다.
- 가맹점 대상은 관계형 FK를 정본으로 사용하고 JSON 조건보다 먼저 판정한다.
- 실적 구간은 우선순위가 아니라 서로 겹치지 않는 조건 범위로 표현한다.
- 기존 단순 룰은 `LEGACY` 계산 경로를 유지하고 검증된 룰부터 단계적으로 전환한다.

## 2. 관련 코드와 데이터

| 구분 | 위치 | 역할 |
| --- | --- | --- |
| JSON 모델 | `domain/benefit/rule/BenefitRuleDefinition.java` | 버전형 조건·산식·한도 모델 |
| JSON 검증 | `domain/benefit/rule/BenefitRuleDefinitionParser.java` | 허용 조건, 연산자, 숫자와 필수값 검증 |
| 통합 Evaluator | `domain/benefit/rule/JsonBenefitRuleEvaluator.java` | `all/any/none` 판정 후 기존 계산기 호출 |
| 조건 Evaluator | `domain/benefit/rule/*RuleConditionEvaluator.java` | 숫자·대상·시간·불리언 조건 판정 |
| 계산 서비스 | `domain/benefit/service/BenefitUsageCalculationService.java` | 승인별 문맥 구성, 계산, 결과·사용 원장 저장 |
| MyBatis | `mapper/benefit/BenefitCalculationMapper.xml` | 후보 룰, 실적, 한도, 일·월 사용 횟수 조회 |
| 스키마 | `V22__add_json_benefit_rule_definitions.sql` | JSON 룰 컬럼과 대표 룰 backfill |
| target 보정 | `V21__backfill_appended_card_benefit_targets.sql` | FK target과 OR 그룹 보정 |
| 초기 seed | `moca_final_seed.sql` | 신규 DB에서도 같은 룰과 target 구성 |

## 3. 전체 처리 흐름

승인내역 한 건은 다음 순서로 처리한다.

1. `user_card` 행을 잠가 같은 카드의 동시 계산을 직렬화한다.
2. 승인시각을 서울 시간으로 변환해 사용일과 전월 실적 월을 구한다.
3. 전월 실적 스냅샷을 조회한다. 행이 없으면 `0원`이 아니라 `데이터 없음`으로 구분한다.
4. 유효기간과 계산 지원 상태를 만족하는 룰 후보를 조회한다.
5. 월 보상 한도와 같은 offer의 확정 일·월 사용 횟수를 조회한다.
6. 관계형 `benefit_rule_targets`를 먼저 평가한다.
7. JSON 룰을 파싱하고 허용된 조건인지 검증한다.
8. `all`, `any`, `none` 조건을 실패-폐쇄 방식으로 평가한다.
9. 조건을 통과하면 `BasicBenefitCalculator`로 산식과 한도를 계산한다.
10. 계산 outcome을 저장하고 실제 적용값이 0보다 크면 확정 사용 원장을 저장한다.

같은 카드의 동시 승인이 들어와도 잠금 이후 사용량을 읽으므로 일·월 횟수와 공유 월 한도를 동시에
소진하는 경쟁을 줄인다. 동일 승인 재처리는 사용 원장의 중복키와 기존 멱등 처리 경계를 따른다.

## 4. DB 컬럼과 지원 상태

`benefit_rules`에 다음 컬럼을 사용한다.

| 컬럼 | 의미 |
| --- | --- |
| `rule_schema_version` | JSON 계약 버전. 현재 `1`만 지원 |
| `rule_support_status` | 계산 지원 범위 |
| `rule_definition_json` | 검증 후 실행하는 JSON 룰 |

지원 상태는 다음과 같이 사용한다.

| 상태 | 계산 여부 | 용도 |
| --- | --- | --- |
| `LEGACY` | 기존 안전 경로 | JSON으로 전환하지 않은 단순 룰 |
| `SUPPORTED` | JSON Evaluator | 필요한 입력을 모두 확보한 룰 |
| `PARTIAL` | JSON Evaluator | 명시된 범위만 계산 가능한 룰 |
| `INFORMATION_ONLY` | 계산 제외 | 안내는 가능하지만 확정 계산 불가 |

`PARTIAL`을 사용했다면 구현한 범위와 미지원 범위를 카드별 테스트와 리뷰 설명에 반드시 남긴다.
예를 들어 SOL Plan 국내 기본 적립은 계산하지만 현재 수집하지 않는 해외 승인까지 지원한다고 표시하면 안 된다.

## 5. JSON DSL 버전 1

### 5.1 기본 구조

```json
{
  "schemaVersion": 1,
  "conditions": {
    "all": [],
    "any": [],
    "none": []
  },
  "reward": {
    "benefitType": "DISCOUNT",
    "rewardUnit": "KRW",
    "calculation": "RATE",
    "rate": "0.10"
  },
  "limits": []
}
```

금액, 비율과 보상값은 JSON 문자열로 저장한다. Java에서는 `BigDecimal`로 변환하며 `double`을 사용하지 않는다.

### 5.2 조건 조합

| 필드 | 의미 | 데이터 부족 시 |
| --- | --- | --- |
| `all` | 모든 조건이 일치해야 함 | 하나라도 판정 불가하면 전체 판정 불가 |
| `any` | 하나 이상의 조건이 일치해야 함 | 일치가 없고 판정 불가 조건이 있으면 전체 판정 불가 |
| `none` | 하나라도 일치하면 제외 | 불일치를 확정할 수 없으면 전체 판정 불가 |

조건마다 `rejectionReason`을 지정할 수 있다. 누락되거나 잘못된 값이면 `CONDITION_NOT_MET`을 사용한다.

```json
{
  "type": "PREVIOUS_MONTH_SPEND",
  "operator": "GTE",
  "value": "500000",
  "rejectionReason": "PERFORMANCE_NOT_MET"
}
```

### 5.3 지원 조건

| 조건 type | 연산자 | 실제 입력 | 비고 |
| --- | --- | --- | --- |
| `PAYMENT_AMOUNT` | `GT/GTE/LT/LTE/EQ` | CODEF 승인금액 | 원 단위 |
| `PREVIOUS_MONTH_SPEND` | `GT/GTE/LT/LTE/EQ` | 실적 스냅샷 | 스냅샷이 없으면 판정 불가 |
| `USED_DAILY_COUNT` | `GT/GTE/LT/LTE/EQ` | 확정 사용 원장 | 동일 offer 기준 |
| `USED_MONTHLY_COUNT` | `GT/GTE/LT/LTE/EQ` | 확정 사용 원장 | 동일 offer 기준 |
| `MERCHANT` | `EQ/IN` | 내부 merchant ID | 관계형 target 선판정도 항상 수행 |
| `MERCHANT_CATEGORY` | `EQ/IN` | 내부 category code 계층 | Kakao 원문명이 아님 |
| `DAY_OF_WEEK` | `IN` | 승인시각의 KST 요일 | `MONDAY` 등 영문 enum |
| `APPROVED_TIME` | `BETWEEN` | 승인시각의 KST 시각 | 시작 포함, 종료 미포함 |
| `FOREIGN_TRANSACTION` | `EQ` | 정규화한 국내외 여부 | 현재 저장 승인 범위는 국내 중심 |
| `NEW_MEMBER_GRACE` | `EQ` | 신규 발급 유예 여부 | 현재 서비스 문맥에서는 보통 판정 불가 |
| `MERCHANT_ELIGIBLE` | `EQ` | 카드사 적격 가맹점 여부 | 근거 데이터가 없으면 판정 불가 |
| `PAYMENT_CHANNEL_ELIGIBLE` | `EQ` | 결제 채널 적격 여부 | 근거 데이터가 없으면 판정 불가 |

`APPROVED_TIME`은 자정을 넘는 구간도 지원한다. 예를 들어 `23:00`부터 `02:00`까지는
`[23:00, 24:00) OR [00:00, 02:00)`로 평가한다.

### 5.4 산식

| calculation | 필수값 | 계산 예 |
| --- | --- | --- |
| `RATE` | `rate` | 결제금액 × 0.10 |
| `FIXED` | `value` | 건당 1,000원 할인 |
| `PER_SPEND_UNIT` | `value`, `spendUnitAmount > 0` | 1,000원당 1포인트 |
| `PER_USAGE_UNIT` | `value` | 이용 1회당 2마일 |

지원 혜택 유형은 `DISCOUNT`, `CASHBACK`, `POINT`, `MILEAGE`이고 보상 단위는 `KRW`, `POINT`,
`MILE`이다. 계산 결과의 원·포인트·마일 미만 값은 기존 계산기 정책에 따라 절사한다.

### 5.5 한도

| limit type | 의미 |
| --- | --- |
| `TRANSACTION_BENEFIT_BASE` | 한 거래에서 혜택 산식에 인정하는 결제금액 상한 |
| `DAILY_USAGE_COUNT` | 동일 offer의 일 허용 횟수 |
| `MONTHLY_USAGE_COUNT` | 동일 offer의 월 허용 횟수 |

월 보상금액 한도와 `shared_group_key`는 기존 `benefit_limit_policies` 및
`benefit_limit_tiers`를 정본으로 유지한다. JSON에 이미 정규화된 월 금액 한도를 중복 저장하지 않는다.

```json
"limits": [
  {"type": "TRANSACTION_BENEFIT_BASE", "value": "50000"},
  {"type": "DAILY_USAGE_COUNT", "value": "1"}
]
```

## 6. 우선순위 대신 상호 배타적 실적 구간 사용

Evaluator는 임의의 우선순위 점수로 하나를 고르지 않는다. 각 룰의 조건을 완전하게 작성해 동시에 적용되지
않도록 한다.

SOL Plan 기본 적립의 예시는 다음과 같다.

- 1% 룰: `PREVIOUS_MONTH_SPEND >= 400000 AND PREVIOUS_MONTH_SPEND < 1000000`
- 1.5% 룰: `PREVIOUS_MONTH_SPEND >= 1000000`

하위 구간에 상한 조건을 넣지 않으면 100만 원 이상에서 1%와 1.5%가 함께 계산될 수 있다. 새로운 구간형
혜택은 각 경계의 `-1`, 경계값, `+1` 테스트를 반드시 작성한다.

DB의 `priority` 컬럼은 기존 정렬과 원문 구조를 위해 남아 있지만 JSON 룰의 조건 누락을 보완하는 수단으로
사용하지 않는다.

## 7. 관계형 가맹점 target 규칙

`benefit_rule_targets`는 검색 후보와 정확한 가맹점 적용 범위의 정본이다.

- 같은 `condition_group`의 include target: AND
- 서로 다른 include 그룹: OR
- exclude target: include보다 우선
- `all_merchants`: 가맹점 마스터가 없어도 일치

따라서 `교보문고 OR YES24`처럼 서로 대체 가능한 브랜드는 서로 다른 condition group을 사용한다.

```text
group 1: merchant=교보문고
group 2: merchant=YES24
```

두 브랜드를 같은 그룹에 넣으면 한 승인 거래가 두 merchant ID를 동시에 가질 수 없어 항상 불일치한다.
V21과 최종 seed는 Z work의 온라인 쇼핑·도서 브랜드를 개별 OR 그룹으로 정규화한다.

## 8. 실패-폐쇄 데이터 경계

Evaluator는 `BenefitCalculationContext.targetAttributes[AVAILABLE_FIELD]`에 포함된 입력만 확정값으로 사용한다.

현재 확정적으로 제공하는 값은 다음과 같다.

- 결제금액
- 승인시각
- 동일 offer의 일·월 확정 사용 횟수
- 정규화된 국내 거래 여부
- 매칭된 merchant ID와 category 계층
- 존재하는 경우에만 전월 실적 스냅샷

다음 항목은 데이터가 확보되기 전까지 적용을 추정하지 않는다.

- 신규 발급월·익월 실적 유예
- 온라인, 오프라인, 일반결제, 간편결제 등 정확한 결제 채널
- 카드사 내부 적격 가맹점 판정
- 상품권, 세금, 수수료, 무이자할부 등 승인 응답에 없는 제외 속성
- 사용자가 선택한 카드 혜택 옵션
- 카드사 청구 확정 시점의 최종 청구할인
- 현재 동기화 범위 밖의 해외 승인
- 공휴일 캘린더가 필요한 평일·주말 예외

필요한 값이 없으면 `RULE_DATA_UNAVAILABLE` outcome을 남기고 확정 사용 이력은 생성하지 않는다. 운영 중
이 사유가 증가하면 임의로 `AVAILABLE_FIELD`를 추가하지 말고 데이터 출처, 정규화 테스트와 보관 정책부터
정의한다.

## 9. 현재 JSON으로 전환한 대표 룰

### 현대카드 Z work Edition2

- 온라인 쇼핑 10% 청구 할인
- 편의점 10% 청구 할인
- 커피전문점 10% 청구 할인
- 도서 10% 청구 할인
- 전월 실적 50만 원, 10% 정률, 일 1회 조건
- 월 금액 한도는 관계형 limit tier 사용

대중교통 혜택은 seed 원문만으로 일 1회 적용 여부를 동일하게 확정하기 어려워 같은 JSON 전환 목록에
넣지 않았다.

### 신한카드 SOL Plan

- 국내/외 전가맹점 기본 적립 1%, 1.5% 실적 구간
- 국내 승인 계산 범위만 구현했으므로 `PARTIAL`
- 해외 승인을 지원한다는 의미가 아님

이 전환은 전체 카드 카탈로그가 JSON 구조화되었다는 뜻이 아니다. 기존 안전한 단순 룰은 `LEGACY` 경로를
사용하며, 복합 룰은 약관 근거와 필수 테스트가 준비된 순서로 전환한다.

## 10. 새로운 JSON 룰 추가 절차

1. 카드사 상품 안내와 구조화 원본에서 정확한 `benefit_title`과 조건 근거를 확인한다.
2. target이 merchant/category FK로 해결되는지 확인한다.
3. 필요한 입력이 CODEF 승인 또는 내부 원장에 있는지 분류한다.
4. 없는 입력이 하나라도 필수라면 `INFORMATION_ONLY` 또는 명시적인 `PARTIAL` 범위로 둔다.
5. 실적 구간이 겹치지 않도록 하한과 상한을 함께 작성한다.
6. JSON을 `rule_schema_version=1`과 함께 저장한다.
7. 모든 필수 입력이 있는 경우에만 `SUPPORTED`로 변경한다.
8. 정상 적용, 조건 미충족, 데이터 없음, 경계값 테스트를 작성한다.
9. seed를 수정했다면 첫 실행과 두 번째 실행의 멱등성을 통합 테스트한다.
10. 전체 품질 검증을 실행한다.

예시 SQL은 다음과 같은 형태다. 실제 ID와 조건은 약관 및 현재 DDL을 확인한 후 작성한다.

```sql
UPDATE benefit_rules
SET rule_schema_version = 1,
    rule_support_status = 'SUPPORTED',
    rule_definition_json = JSON_OBJECT(
        'schemaVersion', 1,
        'conditions', JSON_OBJECT(
            'all', JSON_ARRAY(
                JSON_OBJECT(
                    'type', 'PREVIOUS_MONTH_SPEND',
                    'operator', 'GTE',
                    'value', '500000',
                    'rejectionReason', 'PERFORMANCE_NOT_MET'
                )
            ),
            'any', JSON_ARRAY(),
            'none', JSON_ARRAY()
        ),
        'reward', JSON_OBJECT(
            'benefitType', 'DISCOUNT',
            'rewardUnit', 'KRW',
            'calculation', 'RATE',
            'rate', '0.10'
        ),
        'limits', JSON_ARRAY(
            JSON_OBJECT('type', 'DAILY_USAGE_COUNT', 'value', '1')
        )
    )
WHERE rule_id = :verifiedRuleId;
```

## 11. 테스트 기준

JSON 룰 변경 시 최소한 다음을 검증한다.

- JSON 누락, 문법 오류, 미지원 버전·조건·연산자
- 음수와 잘못된 숫자, 횟수 한도의 소수·정수 초과
- 필수 산식값 누락
- `RATE`, `FIXED`, `PER_SPEND_UNIT`, `PER_USAGE_UNIT`
- `all`, `any`, `none`의 일치·불일치·판정 불가
- 전월 실적 스냅샷 존재·부재
- 일·월 마지막 허용 건과 첫 거절 건
- 관계형 target 일치·불일치
- KST 요일, 일반 시간 구간, 자정을 넘는 구간
- 월 한도 미소진·부분 적용·완전 소진
- MyBatis JSON record 매핑과 offer 단위 사용 횟수 합계
- seed 첫 실행과 재실행 결과 일치

검증 명령:

```bash
./gradlew test
./gradlew integrationTest \
  --tests '*BenefitCalculationMapperIntegrationTest' \
  --tests '*MocaFinalSeedIntegrationTest'
./gradlew check
git diff --check
```

`check`는 Checkstyle과 JaCoCo 라인 커버리지 100% 기준을 포함한다. 통합 테스트는 Docker가 실행 중이어야 한다.

## 12. 운영 진단

지원 상태와 JSON 정합성은 다음 쿼리로 점검한다.

```sql
SELECT rule_support_status, rule_schema_version, COUNT(*) AS rule_count
FROM benefit_rules
GROUP BY rule_support_status, rule_schema_version
ORDER BY rule_support_status, rule_schema_version;

SELECT rule_id, rule_support_status, rule_schema_version
FROM benefit_rules
WHERE (rule_schema_version IS NULL) <> (rule_definition_json IS NULL)
   OR (rule_definition_json IS NOT NULL AND JSON_VALID(rule_definition_json) = 0);

SELECT rejection_reason, COUNT(*) AS outcome_count
FROM user_benefit_calculation_outcomes
WHERE calculated_at >= UTC_TIMESTAMP() - INTERVAL 1 DAY
GROUP BY rejection_reason
ORDER BY outcome_count DESC;
```

`RULE_DATA_UNAVAILABLE`이 반복되는 룰은 필요한 입력을 확보할지, `PARTIAL` 범위를 줄일지,
`INFORMATION_ONLY`로 전환할지를 결정한다. 오류를 숨기기 위해 해당 조건을 삭제하거나 기본값으로 통과시키지 않는다.
