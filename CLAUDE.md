# CLAUDE.md

이 파일은 Claude Code가 세션 시작 시 자동으로 읽는 진입점이다. 저장소의 협업 규칙 원본은 `AGENTS.md`이며, 아래 임포트로 전체 내용을 그대로 불러온다. 이 파일의 나머지 섹션은 실제 작업 시 빠르게 찾아볼 수 있도록 목적별로 정리한 참조표다.

@AGENTS.md

## 아키텍처 규칙

- 호출 방향: `Controller -> Application Service -> Domain Service -> Mapper`
- Controller는 HTTP 요청 변환, 검증, 응답 상태만 담당하고 Mapper를 직접 호출하지 않는다.
- 업무 규칙은 Controller나 SQL이 아닌 Application/Domain Service에 둔다.
- 외부 서비스 DTO는 인프라 계층에서 내부 모델로 변환한다.
- 패키지: `com.moca.mocabe.domain`(기능별 업무 코드), `com.moca.mocabe.global`(MVC 설정·보안·예외 등 공통 코드). 사용하지 않는 빈 계층은 미리 만들지 않는다.

## 인증 규칙

- OAuth 로그인은 Authorization Code + PKCE S256을 사용한다.
- 서버가 관리하는 opaque access/refresh token을 사용하며 JWT는 사용하지 않는다.
- 토큰 원문을 저장하거나 로그에 남기지 않는다.
- 사용자 식별자는 요청값이 아닌 인증 컨텍스트에서 가져온다. 요청 DTO에 `userId`를 받지 않는다.

## Git·커밋·PR 워크플로

1. 작업 전 현재 브랜치와 `git status` 확인.
2. 모든 새 작업은 `git pull --ff-only origin main`으로 동기화한 `main`에서 새 브랜치로 시작한다. `main`에 직접 커밋하지 않는다.
3. 브랜치명: `feature/<name>`, `fix/<name>`, `hotfix/<name>`, `docs/<topic>`, `chore/<topic>`.
4. 부모 이슈 + 하위 이슈 묶음은 하나의 브랜치와 하나의 PR로 진행한다.
5. 커밋 메시지: 한글 Conventional Commits, `<type>(<scope>): <summary>` (`scope`는 kebab-case 필수). 커밋 전 `git status`, staged diff, `git diff --check` 확인. 하나의 커밋에는 하나의 목적만 포함한다.
6. `git push` 전 `./gradlew check` 통과 필수. pre-push JaCoCo 100% 기준을 `--no-verify`로 우회하지 않는다.
7. 커밋·push 요청을 받으면 push로 끝내지 않고 이슈·PR 초안까지 작성한다 (`github-issue-writing` 스킬 참고).

## 검증 체크리스트

- Java 변경: 컴파일 + 핵심 정상 경로 확인, 기본 명령은 `./gradlew test`.
- API 변경: `src/main/resources/openapi/openapi.yaml`의 경로·메서드·인증·요청/응답 스키마·상태 코드를 함께 갱신하고 계층 구조를 재확인한다.
- MyBatis 변경: Mapper 연결과 파라미터 바인딩 확인.
- 문서/YAML 변경: 문법과 `git diff --check` 확인.
- 실제 DB·외부 API 연결, 통합·부하·배포 검증은 명시적으로 요청된 경우에만 수행한다.

## 저장소 스킬 (목적별)

작업 성격에 맞는 스킬을 적용한다. 각 스킬 원본은 `.agents/skills/<name>/SKILL.md`이며 아래 임포트로 전체 내용을 불러온다.

### API 엔드포인트 추가·수정 → `spring-api`

요청·응답 DTO, 입력 검증, 인증·인가, 예외 처리, 서비스 경계, OpenAPI 문서, 테스트를 함께 다뤄야 할 때 적용한다.

@.agents/skills/spring-api/SKILL.md

### MyBatis Mapper·SQL 작업 → `mybatis-feature`

Mapper, XML SQL, 영속 모델을 추가·수정할 때, 조회·저장 기능·SQL 성능·매핑 정확성·트랜잭션 경계를 검토할 때 적용한다.

@.agents/skills/mybatis-feature/SKILL.md

### 카드 혜택 계산 규칙·테스트 → `benefit-calculation-test`

할인·캐시백·포인트·마일리지 계산, 전월 실적·신규 발급 유예, 거래·월 한도, 시간·요일·횟수·가맹점 조건, CODEF 승인내역 기반 역산, 카드고릴라 구조화 룰의 정확성을 검증할 때 적용한다.

@.agents/skills/benefit-calculation-test/SKILL.md

### GitHub 이슈·PR 작성, push 후 인계 → `github-issue-writing`

부모·작업 이슈와 Pull Request 초안을 템플릿에 맞춰 작성할 때, 커밋·push 후 인계 자료를 작성할 때 적용한다.

@.agents/skills/github-issue-writing/SKILL.md
