# 카드 혜택 구조화 2차 확장 — 결과 보고

브랜치: `fix/card-benefit-target-integrity`
작업 범위: `tools/benefit-structuring/generate-safe-structuring.mjs` 확장 및 재생성된
`src/main/resources/db/migration/moca_final_seed.sql`

## 0. 실행 환경 제약 (먼저 밝힌다)

이 작업은 클라우드 컨테이너에서 진행했고, 이 컨테이너의 네트워크 allowlist는 npm/pypi/jsr/
crates/go proxy만 허용하며 **Docker Hub, APT 저장소, Maven Central은 모두 차단**되어 있다.
그 결과:

- `mysql:8.0.36` 이미지를 내려받을 수 없어 실제 MySQL 8을 띄우지 못했다.
- Gradle이 Maven Central에서 의존성을 내려받지 못해 `./gradlew test`,
  `./gradlew check`, `./gradlew integrationTest`를 **한 번도 실행하지 못했다**(컴파일조차
  하지 못했다).

따라서 이번 라운드는 다음 원칙으로 진행했다.

1. Java 계산 로직은 전혀 수정하지 않는다. 대신 실제 계산기(`BasicBenefitCalculator`,
   `BenefitUsageCalculationService`, `findSimpleRulesForUserCard` MyBatis 쿼리)가 **이미
   읽고 있는 컬럼**만 골든 fixture 값으로 채운다. 이 소비 코드는 golden fixture의
   `expectedRawRewardValue`/`expectedAppliedRewardValue`를 만드는 데도 쓰이는 코드이므로
   신뢰도가 높다.
2. 검증 불가능한 새 Java/JSON 로직(JSON Rule DSL 생성 등)은 이번 라운드에 추가하지 않았다.
   대신 정확히 무엇이 막혀 있는지, 다음에 무엇을 하면 되는지 코드 주석과 이 문서에 남겼다.
3. 사용자가 Docker에 접근 가능한 환경에서 아래 명령으로 반드시 재검증해야 한다.

```bash
docker compose up -d mysql
./gradlew flywayMigrate   # 또는 애플리케이션 기동 시 자동 적용
./gradlew integrationTest --tests com.moca.mocabe.domain.benefit.mapper.MocaFinalSeedIntegrationTest --rerun-tasks --no-daemon
mysql ... < src/main/resources/db/audit/card-recommendation-root-cause-audit.sql
```

## 1. 165장 전수 분석

`src/test/resources/benefit/card-benefit-detail-cases-1206.json`(카드 200장, 혜택 1,206건)은
이미 `detailText`를 사람이 규칙별로 구조화한 golden fixture이며, `dailyUsageLimit`,
`monthlyUsageLimit`, `maximumBenefitBaseAmount`, `monthlyLimitValue`, `rewardBasis`,
`rewardValue`, `merchantEligibilityRequired`, `paymentChannelEligibilityRequired` 등 계산에
필요한 값이 이미 숫자로 파싱되어 있다. 즉 "원문 → 파싱"은 이전 라운드에서 이미 끝나 있었고,
이번 라운드가 실제로 손댄 지점은 **"파싱된 값 → 안전한 관계형 rule/target"으로 승격하는
생성기(`generate-safe-structuring.mjs`)의 승격 조건**이었다.

`tools/benefit-structuring/round2/card_recovery_plan.csv`(및 `.json`)에 fixture의 200장 전체에
대해 카드별 `primary_status`(우선순위: READY 우선, 그다음 target/merchant 미매핑, 그다음 횟수
한도, 결제채널, 산식 미지원 순)와 `recoverable_benefit`(그 카드에서 가장 먼저 살릴 수 있는
혜택 제목)을 담았다. fixture에 없는 카드 3장은 포함하지 않았다(§6 참고).

## 2. 반복 패턴 추출과 실제 데이터 분포

기존 생성기가 `MERCHANT_NOT_MAPPED`/`TARGET_NOT_MAPPED`로 뭉뚱그리던 것을 실제 title/category
분포로 분해했다(코드로 재현 가능, 아래는 이번 라운드 시작 시점 스냅샷).

| 유형 | 혜택 수 | 영향 카드 수 |
| --- | ---: | ---: |
| TARGET_NOT_MAPPED (merchant 불필요, category 없음) | 196 | 117 |
| MERCHANT_NOT_MAPPED (브랜드 명시 요구) | 177 | 111 |
| PAYMENT_CHANNEL_UNSUPPORTED | 49 | 39 |
| TRANSACTION_CAP_UNSUPPORTED (구현 전) | 15 | 9 |
| REWARD_BASIS_UNSUPPORTED(FIXED 등, 구현 전) | 15 | 12 |
| DAILY_USAGE_LIMIT_UNSUPPORTED | 7 | 7 |
| MONTHLY_USAGE_LIMIT_UNSUPPORTED | 4 | 3 |
| WEEKEND_UNSUPPORTED | 2 | 2 |
| PERFORMANCE_TIER_UNSUPPORTED | 1 | 1 |

이 표의 reject 사유는 `supported()`가 순서대로 검사하며 첫 실패 사유만 기록하는 cascade
구조라, 앞 단계(merchant/target/reward_basis)를 고치면 뒤에 가려져 있던 사유(횟수 한도,
제외 조건)가 새로 드러난다. 실제로 이번 라운드에서 merchant target을 추가하자, 매칭된 29개
혜택이 전부 `EXCLUSIONS` 또는 횟수 한도에 다시 걸리는 것으로 확인됐다(§4).

## 3. 실제로 구현한 것

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| category 코드 버그 수정 | `PUBLIC_TRANSIT`/`TAXI`/`THEME_PARK`/`ACADEMY`/`AUTO_MAINTENANCE` 5개 코드가 `merchant_categories.category_code`와 불일치해 target 없는 무효 rule을 만들고 있었다. `TRANSPORTATION`/`LEISURE`/`EDUCATION`/`AUTOMOTIVE`로 수정 | `moca_final_seed.sql`의 `INSERT INTO merchant_categories` 실제 코드와 대조 |
| `주유소` 동의어 추가 | `FUEL_CAR` category의 "주유소" 제목 3건 모두 순수 주유 할인/적립으로 확인 후 `주유` 정규식에 합류 | 원문 3건 표본 확인 |
| `FIXED` reward basis 지원 | 정액 할인·캐시백·포인트·마일리지(`rewardValue`)를 `RATE`와 함께 승격 | `BasicBenefitCalculator.calculateRawReward`의 `FIXED` 분기가 이미 지원 |
| 거래 인정금액 상한(`transaction_max_krw`) | `maximumBenefitBaseAmount`를 기존 컬럼에 채움 | `SimpleBenefitRuleRow`→`toRule()`이 이미 `transactionMaxKrw()`를 전달 |
| 월 보상 한도(단일, PERFORMANCE_TIER 제외) | `monthlyLimitValue`를 `benefit_limit_policies`(monthly/reward_amount)+`benefit_limit_tiers`로 저장 | `findSimpleRulesForUserCard`가 LEGACY rule에서 유일하게 허용하는 한도 모양 |
| 명시 브랜드 merchant target | 기존 33개 merchant master를 정규식으로 탐지해 `merchant` target(OR condition group)을 생성하는 경로 추가 | V23의 CU/세븐일레븐 OR 그룹 패턴을 일반화 |
| DECIMAL 부동소수점 버그 수정 | `rewardRate*100`이 `7.000000000000001` 같은 값을 만들던 것을 소수 4자리로 반올림 | 생성 SQL 직접 검사로 발견 |
| CHECK 제약 위반 방지 | `transaction_max_krw < transaction_min_krw`가 되는 조합(3건 발견, 현재는 다른 사유로도 제외됨)을 명시적으로 차단 | `chk_benefit_rules_transaction_range` |

## 4. Coverage Gain (생성기 자체 측정치, 실제 MySQL 미검증)

`node tools/benefit-structuring/generate-safe-structuring.mjs` 실행 결과(재현 가능):

| 단계 | 구조화된 혜택 | 구조화된 카드(자동) |
| --- | ---: | ---: |
| 기존(1차) | 48 | 34 |
| category 코드 버그 수정 + FIXED + transaction cap + monthly 한도 + merchant target 인프라 | 62 | **42 (+8)** |

- `merchantTargetedBenefits: 0` — merchant 매칭에 성공한 29개 혜택은 전부 `EXCLUSIONS` 또는
  일·월 횟수 한도와 동시에 걸려 있어 이번 라운드에서 신규 READY로 전환되지 않았다. 즉 merchant
  resolver 인프라는 만들었지만, 이번 데이터셋에서는 exclusion/count-limit parser가 없으면
  실제 카드 증가로 이어지지 않는다(§5에 다음 단계로 남김).
- `fixedBasisBenefits: 4`, `transactionCapBenefits: 10` — 이 값들이 이번 +8 카드 증가의
  실질적 원인이다.

**주의**: 34→38(READY 총합, 수동 구조화 17장과의 합집합)이라는 기존 공개 수치와 34→42(자동
구조화 카드 수 자체)는 다른 지표다. 자동 구조화가 42로 늘었다고 해서 최종 READY 합계가
반드시 46(=38+8)이 되는 것은 아니다 — 새로 추가된 8장 중 일부가 이미 수동 구조화된 17장과
겹칠 수 있기 때문이다. 정확한 최종 READY 합계는
`card-recommendation-root-cause-audit.sql`을 실제 MySQL에서 실행해야 확정된다(§0).

## 5. Merchant 후보 전수 추출 (1,206건 전체, detailText 포함)

기존 33개 merchant master 브랜드 + 조사용 후보(다이소·쿠팡·G마켓·11번가·티몬·위메프·인터파크·
교보문고·YES24)를 detailText까지 포함해 전수 검색한 결과:

```
merchant candidate 언급 있는 혜택: 378 / 1,206
direct(기존 master 일치) 언급 수: 926
unresolved(신규 canonical 필요) 언급 수: 277
new_canonical_merchants_needed: 쿠팡, 위메프, G마켓, 11번가, 다이소, 교보문고, YES24, 티몬, 인터파크
```

이 중 다이소(27회 언급)만 오프라인 매장이 있는 실질적 신규 merchant 후보다. 나머지(쿠팡·
G마켓·11번가·티몬·위메프·인터파크·교보문고·YES24)는 온라인/앱 구매가 중심이라 지도 기반 오프라인
추천의 범위 밖이며, 임의로 merchant를 새로 만들지 않는다는 정책(§13)에 따라 이번 라운드에서는
추가하지 않았다.

## 6. 아직 안 되는 것과 이유 (전부 억지로 READY 처리하지 않았다)

| 막힌 이유 | 카드 수(추정) | 왜 이번에 안 했는가 | 다음 단계 |
| --- | ---: | --- | --- |
| `EXCLUSIONS`(제외 조건 원문 있음) | 다수(merchant 매칭된 29건 중 대부분) | 제외 조건을 안전하게 구조화하려면 별도 exclusion parser + `match_mode=exclude` 관계형 target 또는 JSON `none` 조건이 필요. 임의로 무시하면 오추천 위험 | exclusion 문구 패턴(상품권 제외/입점매장 제외/온라인 제외 등) 카탈로그화 후 `match_mode=exclude` target 생성 |
| `DAILY_USAGE_LIMIT`/`MONTHLY_USAGE_LIMIT`(횟수 한도) | 7 + 3 | LEGACY 관계형 경로는 이 값을 표현할 수 없다(`toRule()`이 항상 0 고정, `findSimpleRulesForUserCard`가 count-limit `benefit_limit_policies`가 있으면 rule 자체를 제외). JSON Rule DSL로만 가능한데 이 환경에서 빌드/테스트를 못 돌려 검증 없이 커밋할 수 없었다 | `rule_definition_json`에 `DAILY_USAGE_COUNT`/`MONTHLY_USAGE_COUNT` limit을 채운 JSON을 생성하고, `BenefitRuleDefinitionParserTest`/`JsonBenefitRuleEvaluator` 단위테스트로 실제 빌드 가능한 환경에서 검증 |
| `PERFORMANCE_TIER`(실적 구간) | 1(+ 앞 사유에 가려진 다수) | fixture가 tier 사다리 전체가 아니라 대표값 1개만 제공해, 단일 월 한도로 저장하면 다른 실적 구간 사용자에게 잘못된 한도를 보여줄 위험 | `card_performance_tiers` 스키마를 활용해 detailText의 "30만원~50만원: X / 50~100만원: Y / 100만원 이상: Z" 패턴을 파싱하는 별도 parser 필요 |
| `PAYMENT_CHANNEL_UNSUPPORTED` | 39 | 간편결제/앱결제 등은 현재 추천 API가 결제수단 컨텍스트를 받지 않아 판정 불가(BENEFIT_RULE_EVALUATOR.md §8) | API 계약에 선택적 `paymentChannel` 필드 추가 논의 필요(별도 제품 결정) |
| `TARGET_NOT_MAPPED` 잔여(약 90여 건) | - | "적립"/"기타"/"생활"/"할인"/"쇼핑" 같은 제목은 실제로는 여러 category를 묶은 복합 혜택으로 확인되어(예: "음식점, 커피전문점, 편의점, 약국 업종 10%") 카테고리 하나로 단순 매핑하면 오분류. 카드별 수동 검토가 필요 | 카드별 detailText를 다시 읽어 명시 브랜드/카테고리 조합별로 V23 스타일 수동 OR-그룹 target을 추가 |
| `해외`/`OVERSEAS` 카테고리 | - | 서비스가 해외 승인을 수집하지 않음(BENEFIT_RULE_EVALUATOR.md §8) | 범위 밖으로 유지 |

카드별 상세(158장, `primary_status`+`recoverable_benefit`)는
`tools/benefit-structuring/round2/card_recovery_plan.csv`에 있다.

## 7. 회귀 확인

기존 필터 로직(`supported()`가 이전에 통과시키던 48개 혜택)은 이번 변경에서 배제 조건을
하나도 더 엄격하게 만들지 않았고(카테고리 코드만 실제 존재하는 코드로 교체), 실제로 이전
48개 전부 새 62개 안에 부분집합으로 포함된다(생성기가 카테고리 매핑을 넓히기만 했을 뿐 좁히지
않았기 때문). 다만 이는 생성기 로직 검토로 확인한 것이며, 기존 수동 구조화 17장을 포함한 DB
차원의 회귀는 실제 MySQL에서 `MocaFinalSeedIntegrationTest`를 돌려야 최종 확인된다.

## 8. 재현 방법

```bash
node tools/benefit-structuring/generate-safe-structuring.mjs
```

이 명령은 결정적(deterministic)이며 `moca_final_seed.sql`의
`-- BEGIN/END GENERATED SAFE BENEFIT STRUCTURING` 구간만 갱신한다.
