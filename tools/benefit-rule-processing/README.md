# 복합 혜택 룰 프로세서

1차 안전 정규화 결과를 입력으로 받아 JSON 룰 DSL과 적립 중첩 방식을 생성하는 검토용 프로세서다.
운영 DB를 직접 변경하지 않으며 결과를 JSON 리포트와 SQL 후보로 분리한다.

## 실행

```bash
npm run test:benefit-processing
npm run benefits:complex
```

다른 입력·출력 경로는 다음처럼 지정한다.

```bash
node tools/benefit-rule-processing/generate-complex-benefit-rules.mjs \
  --input=/absolute/path/input.json \
  --output-dir=/absolute/path/output
```

기본 출력은 `tmp/benefit-rule-processing`에 생성된다.

- `complex-benefit-rules.json`: 전체 판정, 미지원 조건, 중첩 방식
- `complex-benefit-rules.sql`: 완전 구조화된 `STRUCTURED` 후보만 포함하는 검토용 SQL

## 모듈

- `condition-analyzer`: 실적, 최소 결제금액, 횟수, 시간·요일 조건과 DSL 한도 생성
- `stacking-analyzer`: `additive`, `highest_only`, `replace`, `not_stackable`, `standalone` 판별
- `rule-definition-builder`: 보상 산식과 조건을 schema version 1 JSON DSL로 조립
- `dsl-validator`: 백엔드 `BenefitRuleDefinitionParser` 허용 범위와 호환성 검사
- `benefit-processor`: `STRUCTURED`, `PARTIAL`, `PARSE_FAILED`, `NON_MONETARY`, `EXCLUDED` 분류
- `sql-generator`: 완전 구조화 결과만 DB 반영 후보로 변환

추가 프로세싱은 제외 거래 유형, 결제 채널 방식, 전월 실적별 월 한도를 `policies`로
추출한다. 승인내역에 필요한 런타임 입력이나 관계형 한도 정책이 아직 연결되지 않은 결과는
`pendingRuntimeRequirements`에 원인을 남기고 `PARTIAL`을 유지한다. 문구 해석만으로
`STRUCTURED`로 올려 잘못 계산하지 않는다.

`PERFORMANCE_TIER`, `EXCLUSIONS`, `CAPTURE_ORDER`, 결제 채널처럼 입력만으로 안전하게
확정할 수 없는 조건은 임의로 축약하지 않고 `PARTIAL`로 남긴다. 이 결과는 SQL에 포함되지 않는다.
