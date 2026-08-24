-- 결제수단 별칭 기반 정규화는 애플리케이션에서 사용하지 않는다.
-- 자식 테이블을 먼저 제거해 payment_methods 외래 키 제약을 안전하게 해제한다.
DROP TABLE IF EXISTS payment_method_aliases;
DROP TABLE IF EXISTS payment_methods;
