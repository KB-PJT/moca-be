# MOCA Backend

MOCA Backend 초기 개발을 위한 기본 협업 규칙이다.

## Project

- Java 17
- Spring Framework Legacy
- Spring MVC
- Spring Security
- MyBatis
- MySQL 8
- Gradle
- Tomcat 9

## Repository layout

- `src/main/java`: 애플리케이션 코드
- `src/main/resources`: 설정과 MyBatis Mapper XML
- `src/test`: 테스트 코드
- `.github`: Issue 및 Pull Request 템플릿
- `.agents/skills`: 저장소 전용 작업 지침

애플리케이션 코드는 `com.moca.mocabe` 패키지 아래에 작성하고, 테스트는 대응하는 테스트 패키지에 작성한다.

- `com.moca.mocabe.domain`: 기능별 업무 코드
- `com.moca.mocabe.global`: MVC 설정, 보안, 예외 등 공통 코드

기능 패키지는 필요에 따라 `controller`, `dto`, `service`, `domain`, `mapper`로 나누며 사용하지 않는 빈 계층은 미리 만들지 않는다.

## Architecture

기본 호출 방향은 다음과 같다.

`Controller -> Application Service -> Domain Service -> Mapper`

- Controller에서 Mapper를 직접 호출하지 않는다.
- 업무 규칙을 Controller나 SQL에 작성하지 않는다.
- 외부 서비스 DTO는 인프라 계층에서 내부 모델로 변환한다.

## Authentication

- OAuth 로그인은 Authorization Code 방식과 PKCE S256을 사용한다.
- 서버가 관리하는 opaque access token과 refresh token을 사용하며 JWT는 사용하지 않는다.
- 토큰 원문을 저장하거나 로그에 남기지 않는다.
- 사용자 식별자는 요청값이 아닌 인증 정보에서 가져온다.

## Collaboration workflow

1. 작업 전에 현재 브랜치와 `git status`를 확인한다.
2. 모든 새 작업은 현재 브랜치와 무관하게 반드시 최신 `main`에서 시작한다. 먼저 `git pull --ff-only origin main`으로 동기화한 뒤 `main` 기반 작업 브랜치를 만든다.
3. 작업 목적에 따라 `feature/<feature-name>`, `fix/<fix-name>`, `hotfix/<hotfix-name>`, `docs/<topic>`, `chore/<topic>` 브랜치를 최신 `main`에서 만든 후 작업한다.
4. `main`에 기능을 직접 커밋하지 않는다.
5. 현재 브랜치의 목적과 다른 새 작업은 같은 브랜치에서 이어서 진행하지 않는다. 변경 사항을 정리한 뒤 최신 `main`에서 작업 목적에 맞는 새 브랜치를 만든다.
6. 하나의 부모 이슈와 이에 연결된 하위 이슈 묶음은 하나의 기능 브랜치와 하나의 Pull Request로 진행한다.
7. Pull Request 본문에는 완료한 모든 하위 이슈를 `Closes #번호`로 연결하고, 부모 이슈는 모든 하위 이슈와 Pull Request가 완료된 뒤 직접 종료한다.
8. 기존 변경 사항과 관련 없는 IDE·개인 설정 파일은 수정하거나 커밋하지 않는다.

## Commit rules

- 커밋 전 `git status`, staged diff, `git diff --check`를 확인한다.
- 커밋은 하나의 목적만 포함하며 관련 없는 파일을 함께 넣지 않는다.
- 커밋 메시지는 기능 범위를 포함한 한글 Conventional Commits 형식인 `<type>(<scope>): <summary>`를 사용한다.
- `scope`는 변경 기능 또는 영역을 영문 소문자·kebab-case로 작성하며 생략하지 않는다. 예: `auth`, `health-api`, `quality`, `github-flow`.
- 기본 type은 `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `chore`이다.

예시:

```text
feat(auth): access token 발급 기능 추가
build(quality): Checkstyle 검증 추가
docs(github-flow): 이슈 템플릿 보완
```

## Pull Request

- 저장소의 Pull Request 템플릿을 사용한다.
- 자식 이슈는 `Closes #번호`로 연결한다.
- 변경 내용과 실행한 검증을 작성한다.
- `git push` 전에는 `./gradlew check`를 통과해야 한다. pre-push 훅의 JaCoCo 100% 기준을
  `--no-verify`로 우회해 push하지 않는다.

## Push handoff

사용자가 커밋과 push를 요청하면 push 성공으로 작업을 끝내지 않고 이슈와 Pull Request 초안까지 함께 작성한다.

1. 현재 브랜치의 `main` 대비 커밋과 diff를 확인해 실제 반영된 변경만 기준으로 작성한다.
2. `.agents/skills/github-issue-writing/SKILL.md`와 저장소 이슈 템플릿을 사용해 작업 이슈 제목과 본문을 작성한다.
3. `.github/pull_request_template.md`를 사용해 Pull Request 제목과 본문을 작성하고 변경 내용, 리뷰 포인트, 실행한 검증을 구체적으로 적는다.
4. 작업 이슈 번호를 알면 Pull Request에 `Closes #번호`를 넣고, 번호가 없으면 `Closes #작업-이슈-번호` 자리표시자를 사용한다. 부모 이슈는 Pull Request에서 직접 종료하지 않는다.
5. 사용자가 GitHub 이슈나 Pull Request의 실제 생성을 명시하지 않았다면 외부 상태를 변경하지 않고 복사 가능한 초안만 제공한다.
6. 최종 인계에는 push한 브랜치와 커밋, 검증 결과, 작업 이슈 초안, Pull Request 초안을 함께 포함한다.

## Basic verification

- Java 변경은 컴파일과 핵심 정상 경로만 확인한다.
- API 변경 시 `src/main/resources/openapi/openapi.yaml`의 경로, HTTP 메서드, 인증 요구사항,
  요청·응답 스키마, 상태 코드와 설명을 반드시 함께 갱신한다.
- API 변경은 계층 구조와 요청·응답 형태, Swagger/OpenAPI 계약 반영 여부를 확인한다.
- MyBatis 변경은 Mapper 연결과 파라미터 바인딩을 확인한다.
- 문서와 YAML 변경은 문법과 `git diff --check`를 확인한다.
- 기본 Java 검증 명령은 `./gradlew test`이다.
- 실제 DB·외부 API 연결, 통합·부하·배포 검증은 요청된 경우에만 수행한다.

## Repository skills

- `spring-api`: Spring MVC API 작업
- `mybatis-feature`: MyBatis Mapper와 SQL 작업
- `benefit-calculation-test`: 카드 혜택 계산 규칙과 CODEF 역산 테스트 작업
- `github-issue-writing`: GitHub 부모·작업 이슈와 Pull Request 초안을 템플릿에 맞춰 작성하고 push 후 인계 자료를 구성
