---
name: spring-api
description: Spring MVC API endpoint를 추가·수정할 때 사용한다. 요청·응답 DTO, 입력 검증, 인증·인가, 예외 처리, 서비스 경계와 테스트를 함께 구현해야 하는 작업에 적용한다.
---

# Spring API

1. Controller는 HTTP 요청 변환, 검증, 응답 상태만 담당한다.
2. 유스케이스는 Application Service에 두고 Mapper를 Controller에서 직접 호출하지 않는다.
3. 요청 DTO에 `userId`를 받지 말고 인증 컨텍스트의 사용자만 사용한다.
4. 상태 변경에는 트랜잭션 경계를 선언하고, 오류 응답은 프로젝트 공통 형식으로 매핑한다.
5. 정상·검증 실패·미인증 또는 권한 없음 경로를 테스트한다.
