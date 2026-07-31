---
name: mybatis-feature
description: MyBatis Mapper, XML SQL, 영속 모델을 추가·수정할 때 사용한다. 조회·저장 기능, SQL 성능과 매핑 정확성, 트랜잭션 경계를 검토해야 하는 작업에 적용한다.
---

# MyBatis Feature

1. Mapper는 영속성 접근만 담당하고 업무 규칙은 Domain 또는 Application Service에 둔다.
2. 파라미터 바인딩을 사용하고 문자열 조합으로 SQL을 만들지 않는다.
3. 조회 모델과 도메인 모델의 변환 책임을 명확히 하며 외부 연동 DTO를 Mapper에 전달하지 않는다.
4. 변경 쿼리는 서비스 트랜잭션 안에서 수행한다.
5. 핵심 쿼리는 빈 결과, 경계값, 정렬·페이징 및 롤백 경로를 검증한다.
