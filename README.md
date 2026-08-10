# MOCA Backend

사용 가능한 카드 혜택을 분석해 결제 전에 가장 유리한 카드를 추천하는 백엔드 API 서버입니다.

## 기술 스택 및 의존성

| 구분 | 추가 의존성 | 용도 |
| --- | --- | --- |
| 언어·실행 | Java 17, Gradle 8.14.4 Wrapper, Tomcat 9 / Servlet 4.0 | Legacy Spring MVC WAR 실행 환경 |
| 웹·트랜잭션 | Spring Web MVC 5.3, Spring JDBC, Spring TX | REST API, JDBC, 선언적 트랜잭션 |
| 보안 | Spring Security Core / Config / Web / OAuth2 JOSE | Google ID Token 검증, MOCA opaque token 인증·인가 |
| 영속성 | MyBatis 3.5, MyBatis-Spring 2.1, MySQL Connector/J 8.4 | SQL Mapper와 MySQL 8 연결 |
| 스키마 관리 | Flyway Core / MySQL 12.10 | 애플리케이션 시작 시 초기 스키마 적용 |
| 캐시·세션 | Spring Data Redis 2.7, Lettuce 6.1 | access·refresh token 해시 기반 Redis 세션 관리 |
| 검증 | Hibernate Validator 6.2, Jakarta EL 구현체 | 요청 DTO 입력값 검증 |
| API 문서 | OpenAPI 3, Swagger UI WebJar 5.32 | API 계약 및 로컬 Swagger UI 제공 |
| 개발 편의 | Lombok 1.18 | 반복 코드 감소 |
| 테스트·품질 | JUnit 5, Mockito 5, Testcontainers MySQL 2.0, JaCoCo, Checkstyle | 단위·MySQL 통합 테스트, 커버리지, 스타일 검사 |

Spring Framework 5.3과 Tomcat 9은 `javax.servlet` 기반 레거시 환경을 유지하기 위한 선택입니다. 운영 배포 전에 Spring 5.3 보안 패치 수급 방안을 별도로 검토해야 합니다.

## 프로젝트 구조

```text
src/main/java/com/moca/mocabe
└── global
    ├── config    # Spring MVC 설정
    └── health    # 상태 확인 API

src/main/resources
├── openapi       # OpenAPI 계약
└── swagger-ui    # MOCA용 Swagger UI 진입점
```

업무 기능은 구현 시점에 `com.moca.mocabe.domain.<기능명>` 아래에 추가합니다.

## 로컬 실행 준비

1. JDK 17을 사용합니다. 저장소의 `.java-version` 값도 17입니다.
2. Tomcat 9에 WAR를 배포할 수 있어야 합니다.
3. 다음 명령으로 빌드합니다.

```bash
./gradlew clean check war
```

생성된 WAR는 `build/libs/moca-be-1.0-SNAPSHOT.war`입니다.

## 기본 엔드포인트

Tomcat에 `moca-be` 컨텍스트로 배포한 경우:

- Health API: `GET /moca-be/api/v1/health`
- Swagger UI: `/moca-be/swagger-ui`
- OpenAPI YAML: `/moca-be/api-docs/openapi.yaml`

## 로컬 MySQL과 Docker 통합 테스트

로컬 MySQL은 Docker Compose로 실행합니다. `.env.example`을 복사한 뒤 필요한 값을 수정합니다.

```bash
cp .env.example .env
docker compose up -d mysql
docker compose ps
```

애플리케이션 시작 시 Flyway가 `src/main/resources/db/migration/V1__create_initial_schema.sql`을 적용해
현재 초기 개발에 필요한 사용자·알림 설정 테이블을 한 번에 생성합니다. 이 초기화 방식은 아직
개발 데이터가 없는 상태를 전제로 합니다. 기존에 생성된 로컬 DB가 있다면 Docker 볼륨 또는 스키마와
`flyway_schema_history`를 함께 비운 뒤 다시 시작해야 합니다.
컨테이너를 중지하려면 `docker compose down`을 실행합니다. 로컬 DB 볼륨까지 삭제하려면
`docker compose down -v`를 사용합니다.

기본 단위 테스트는 Docker 없이 실행됩니다.

```bash
./gradlew test
```

Docker 기반 MySQL 통합 테스트는 Testcontainers로 매번 독립 컨테이너를 생성합니다.

```bash
./gradlew integrationTest
```

`integrationTest`를 실행하려면 Docker Desktop 또는 Docker Engine이 실행 중이어야 합니다.

### 외부 Tomcat 환경 변수

외부 Tomcat 배포에서는 `$CATALINA_BASE/bin/setenv.sh`에 DB·Redis·보안 값을 설정합니다. 프로젝트 루트가
Tomcat의 작업 경로와 다르면 `.env`를 자동으로 찾을 수 없으므로, 필요할 때만 다음처럼 절대 경로를 지정합니다.

```sh
export MOCA_ENV_FILE='/absolute/path/to/moca-be/.env'
```

운영 환경은 `.env` 대신 `MOCA_DB_PASSWORD`, `MOCA_TOKEN_HASH_PEPPER` 등을 운영 비밀 관리 수단으로 직접
주입하는 방식을 권장합니다. `MOCA_REFRESH_COOKIE_SECURE`는 local HTTP에서는 `false`, HTTPS 배포에서는
반드시 `true`로 설정합니다.

## 테스트와 커버리지

```bash
./gradlew clean check
```

JaCoCo 라인 커버리지 하한선은 현재 100%입니다. HTML 리포트는 `build/reports/jacoco/test/html/index.html`에 생성됩니다.

## 코드 스타일 검사

Java 코드에는 ESLint와 같은 역할로 Checkstyle을 적용합니다. `./gradlew check`를 실행하면 컴파일·테스트·JaCoCo와 함께 main·test 소스의 코드 스타일을 검사합니다.

검사 규칙은 `config/checkstyle/checkstyle.xml`에서 관리하며, 초기에는 import, 탭 문자, 줄 길이(120자), 중괄호와 공백 규칙을 검사합니다. HTML 리포트는 `build/reports/checkstyle/main.html`과 `build/reports/checkstyle/test.html`에서 확인할 수 있습니다.

## Git Hooks

macOS와 Windows에서 같은 검증을 실행하기 위해 Husky를 사용합니다. Node.js 20 이상과 Git을 설치한 뒤 저장소 최상단에서 한 번 실행합니다.

```bash
npm install
```

`npm install`의 `prepare` 스크립트가 Husky를 설치합니다. Git for Windows를 설치한 Windows 환경에서는 Git Bash를 함께 설치해야 합니다.

- 커밋 전: staged diff 공백 오류 검사 후 `./gradlew check` 실행
- push 전: `./gradlew check` (Windows에서는 `gradlew.bat check`) 실행
- pull 병합 후 및 rebase pull 후: `./gradlew test` (Windows에서는 `gradlew.bat test`) 실행

Git에는 `pre-pull` 훅이 없으므로 pull 후 검증에는 `post-merge`와 `post-rewrite` 훅을 사용합니다. 훅은 실패 시 오류를 보여 주지만 pull 완료 자체를 되돌리지는 않습니다.

GUI에서 Node 버전 관리자를 사용한다면 Husky가 Node를 찾을 수 있도록 사용자별 Husky 초기화 파일에 해당 초기화 설정을 추가합니다. macOS/Linux는 `~/.config/husky/init.sh`, Windows는 `C:\\Users\\<사용자>\\.config\\husky\\init.sh`를 사용합니다.

## Pull Request 자동 리뷰

CodeRabbit으로 `main`과 `dev` 대상 Pull Request를 한국어로 자동 리뷰합니다. Draft PR과 `do-not-review` 라벨이 있는 PR은 자동 리뷰에서 제외합니다.

사용하려면 GitHub 조직에 CodeRabbit App을 설치하고 이 저장소에 대한 접근을 허용해야 합니다. 세부 리뷰 기준은 루트의 `.coderabbit.yaml`과 `AGENTS.md`에서 관리합니다.

## API 문서 관리

현재 Swagger 문서는 Spring Boot 자동 설정 없이 정적 OpenAPI 계약을 사용합니다. REST API를 추가하거나 변경하면 `src/main/resources/openapi/openapi.yaml`도 함께 수정해야 합니다.

## 라이선스

이 프로젝트는 [MIT License](LICENSE)를 따릅니다.
