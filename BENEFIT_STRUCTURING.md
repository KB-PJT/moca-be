# 카드 혜택 구조화

## 목적

카드 상세 원문이 존재하더라도 `offer -> rule -> target`이 없으면 지도 추천에 사용할 수 없다.
이 작업은 추천 Mapper의 JOIN을 완화하지 않고, 계산과 대상이 명확한 혜택만 구조화한다.

## 생성 방법

```bash
node tools/benefit-structuring/generate-safe-structuring.mjs
```

생성기는 `card-benefit-detail-cases-1206.json`의 `detailText` 기반 Golden fixture를 읽어
`src/main/resources/db/migration/moca_final_seed.sql`의 생성 구간을 갱신한다.

생성 파일을 직접 수정하지 않는다. 원문 또는 분류 규칙을 수정한 뒤 생성기를 다시 실행한다.

## 안전 구조화 조건

seed 생성기는 보수적인 Golden fixture 범위를 유지한다. 운영 공통 parser 배치는 다음 조건을 모두
확정할 수 있는 혜택만 추가 구조화한다.

- 직접 오프라인 결제 혜택
- 제목과 Golden fixture 카테고리가 동일한 의미로 교차 검증됨
- 할인·캐시백·포인트·마일리지 보상값과 단위가 확정됨
- 전월 실적 구간, 최소·최대 결제금액을 숫자로 확정할 수 있음
- 시간·요일, 일·월 횟수와 거래당 인정금액 상한을 JSON DSL에 손실 없이 투영할 수 있음
- 단일 월 보상 한도는 `benefit_limit_policies`와 `benefit_limit_tiers`로 저장할 수 있음
- category, 전 가맹점 또는 내부 merchant master의 명시 브랜드 target이 확정됨

브랜드가 명시된 혜택은 카테고리 전체로 확장하지 않는다. `GS25/CU`를 편의점 전체로 바꾸는
것과 같은 변환은 금지하며 merchant alias와 FK를 확정한 뒤 별도 구조화한다.

일·연 금액 한도, 복수 월 한도와 공휴일 조건은 현재 계산 문맥으로 확정하지 않는다. 배치는 해당
혜택을 `PARTIAL`로 표시하고 `structuring_note`에 보류 원인을 남긴다. 보상값 또는 target 자체가
불명확한 혜택은 기존 `PARSE_FAILED` 상태를 유지한다.

## 운영 배치

운영 배치는 기본 비활성이다. 빈 DB seed와 통합 테스트를 먼저 검증한 뒤 다음 환경 변수를 설정하면
매일 03:30(Asia/Seoul)에 `RAW`·`PARSE_FAILED` 후보를 다시 평가한다.

```sh
export MOCA_BENEFIT_STRUCTURING_ENABLED=true
```

rule, target, 월 한도 policy/tier와 상태 변경은 하나의 트랜잭션으로 저장된다. category 또는 merchant
FK를 찾지 못하면 전체 변경을 롤백하며 `STRUCTURED`로 표시하지 않는다.

## 현재 결과

Golden fixture 1,206건 중 48개 혜택(34개 카드)이 안전 자동 구조화 후보로 선택된다. 기존 수동
구조화와 중복을 제외하면 신규 룰 33개가 생성되고, 빈 MySQL 8에서 전체 추천 가능 카드는
17장에서 38장으로 증가한다. 여기에는 명확한 category와 `ALL_MERCHANTS` 단순 비율 혜택이
포함된다.

## 90% coverage 분석

Golden fixture에서 직접 오프라인 계산 후보를 하나라도 가진 카드는 177장이다. 검토 필요
오프라인 혜택까지 합쳐도 신규 카드는 7장뿐이므로 현재 데이터의 이론적 상한은 184장이다.
따라서 183장을 달성하려면 제외·merchant·결제 채널 조건이 있는 거의 모든 카드를 지원해야 한다.

현재 추천 API는 가맹점, 결제 예정 금액, 장소 신뢰도만 받는다. 상품권 구매 여부, 백화점
입점매장 여부, PG·간편결제 채널처럼 제외 조건 판정에 필요한 거래 문맥이 없으므로 이 조건을
무시한 90% 달성은 False Positive를 만든다. 다음 단계는 추천 요청에 선택적인 거래 문맥을
추가하거나, 판별 불가능한 조건을 사용자에게 명시하는 `conditional` 추천 계약을 합의하는 것이다.

## Root-cause와 coverage 감사

운영 DB에서는 `src/main/resources/db/audit/card-recommendation-root-cause-audit.sql`을 실행한다.
이 감사는 최신 콘텐츠 버전만 사용하며, `READY`, `OUT_OF_SCOPE`, `BLOCKED`와 **상호 배타적인**
primary blocker를 카드마다 하나만 반환한다. 따라서 세 범주의 합계는 반드시 203이다.

`MERCHANT_UNMAPPED`는 특정 브랜드 원문을 canonical merchant로 연결하지 못해 merchant FK와
target을 만들 수 없는 근본 원인이다. 이때 `TARGET_UNMAPPED`는 secondary symptom으로만 유지한다.
두 항목의 benefit/card 단위 only·intersection 집계를 함께 보므로, 서로 중복된 영향 수를 더해
우선순위를 정하지 않는다.

서비스 범위 KPI는 두 개를 동시에 관리한다.

- `TOTAL_CARD_COVERAGE`: `READY / 203`
- `IN_SCOPE_CARD_COVERAGE`: `READY / 국내 오프라인 금전성 혜택 보유 카드`

fixture 원문을 기준으로 직접 계산 가능한 오프라인 카드는 177장이고, 검토 필요 오프라인 카드를
포함한 이론적 상한은 184장이다. 따라서 전체 203장의 90%(183장)는 가능하더라도 조건부·채널·제외
정책을 거의 모두 안전하게 지원해야 한다. 반면 scope KPI의 90%는 분모를 임의로 낮춘 값이 아니라
원문과 지도 서비스 정책으로 확인 가능한 오프라인 금전성 범위를 분모로 사용한다.

### Merchant 보강 원칙

`merchant_aliases`의 기존 정규화 방식(NFKC, 대문자화, 공백·특수문자 제거)을 그대로 사용한다.
이번 seed는 `GS 25/지에스25/GS리테일 GS25`, `스타벅스커피/스타벅스 코리아/STARBUCKS`,
`씨지브이/CJ CGV`를 기존 GS25·스타벅스·CGV merchant에 alias로 연결한다. 새 merchant row를
만들지 않으며, 명시 브랜드 혜택을 category target으로 넓히지 않는다.

alias 보강만으로는 제외 조건·횟수·채널·산식이 남은 혜택을 READY로 만들지 않는다. 그래서
개선 전후 READY 차이와 “영향 카드 수”를 혼동하지 않는다.

### API context 결정

| Context | 취급 |
| --- | --- |
| `expectedPaymentAt` | 서버 시간으로 자동 판단 |
| `isOnline` | merchant/location metadata로 우선 판단 |
| `paymentChannel`, `walletProvider` | 실제 결제수단 선택 흐름이 있을 때만 선택값 |
| `isGiftCardPurchase`, `isTenantStore`, `transactionCategory` | 매 추천마다 사용자에게 받지 않음; merchant metadata 또는 `CONDITIONAL` 설명 후보 |

`CONDITIONAL`은 예상 금액 순위의 READY와 섞지 않는다. 예를 들어 상품권 제외를 판별할 수 없으면
“상품권 구매 제외”를 노출할 수는 있지만, 확정 할인으로 계산하지 않는다.

나머지는 단순 실패로 숨기지 않는다. 생성기 출력과
`src/main/resources/db/audit/card-recommendation-audit.sql`을 사용해 다음 원인으로 분류한다.

- 온라인·간접결제 또는 비금전성 정보
- target 미매핑
- 특정 merchant 미매핑
- 결제 채널 미지원
- 고정·사용량 기반 산식 미지원
- 거래당 인정금액 상한, 횟수, 구간, 요일 등 조건 미지원

## 검증

```bash
./gradlew integrationTest \
  --tests com.moca.mocabe.domain.benefit.mapper.MocaFinalSeedIntegrationTest \
  --rerun-tasks --no-daemon
```

테스트는 빈 MySQL 8에 전체 migration과 seed를 적용하고 다음을 검증한다.

- seed 재실행 멱등성
- 모든 지원 룰의 include target 및 FK 정합성
- 기존 명시 merchant target의 과잉 category 확장 방지
- 자동 구조화 룰 수와 추천 가능 카드 증가
- 대표 정상 사례(Deep Oil 편의점)와 오분류 방지 사례(K-패스 통신)
- 운영 공통 parser의 rule·target·월 한도 원자 저장과 FK 실패 전체 롤백

구조화 범위를 늘릴 때는 parser를 느슨하게 만들지 말고, 새로운 산식이나 조건을 Evaluator에서
표현하고 경계 테스트를 추가한 다음 후보 제한을 해제한다.
