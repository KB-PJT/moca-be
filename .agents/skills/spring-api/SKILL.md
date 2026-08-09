---
name: spring-api
description: Spring MVC API endpoint를 추가·수정할 때 사용한다. 요청·응답 DTO, 입력 검증, 인증·인가, 예외 처리, 서비스 경계, OpenAPI 상세 문서와 테스트를 함께 구현·검토해야 하는 작업에 적용한다.
---

# Spring API

1. Controller는 HTTP 요청 변환, 검증, 응답 상태만 담당한다.
2. 유스케이스는 Application Service에 두고 Mapper를 Controller에서 직접 호출하지 않는다.
3. 요청 DTO에 `userId`를 받지 말고 인증 컨텍스트의 사용자만 사용한다.
4. 상태 변경에는 트랜잭션 경계를 선언하고, 오류 응답은 프로젝트 공통 형식으로 매핑한다.
5. 정상·검증 실패·미인증 또는 권한 없음 경로를 테스트한다.

## OpenAPI 문서화

API를 추가하거나 계약을 변경하면 `src/main/resources/openapi/openapi.yaml`을 같은 변경에 포함한다.

1. 모든 operation에 `summary`, `description`, `operationId`, 인증 요구사항, 성공·실패 상태 코드를 작성한다.
2. operation 설명에는 대상 사용자, 처리 결과, 기본값·시간대·집계 기준과 데이터가 없거나 지원하지 않는 경우를 적는다. 응답 객체가 중첩되거나 목록이면 최상위 필드·목록 항목·null 또는 빈 배열의 의미를 요약한다.
3. `$ref`가 아닌 path/query/header parameter에는 용도, 허용 범위와 기본값을 작성한다.
4. 요청·응답 모델의 도메인 필드에는 의미와 단위·형식, nullable 또는 enum 값의 의미를 작성한다. 공통 envelope 필드는 스키마 설명으로 문서화한다.
5. 오류 응답에는 발생 이유와 클라이언트가 구분할 오류 코드, 외부 연동 오류의 재시도 가능 여부를 작성한다.
6. 추정 결과·미지원 조건·외부 데이터 한계는 확정 결과처럼 표현하지 않는다.
7. 변경 뒤 YAML 파싱과 `git diff --check`를 수행한다.
