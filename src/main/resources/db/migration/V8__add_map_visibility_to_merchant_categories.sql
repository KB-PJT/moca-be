-- 카테고리를 지도(근처 가맹점 조회)에 노출할지 여부다. kakao_category_maps는 "카카오 API를 어떻게
-- 부를지"(그룹코드 검색 vs 키워드 검색)만 다루는 기술 테이블로 좁히고, "지도에 보여줄지"는 여기서
-- 직접 관리한다. 물리적 위치가 없는 카테고리(간편결제/OTT 등)는 FALSE로 둔다.
ALTER TABLE merchant_categories
    ADD COLUMN is_map_visible BOOLEAN NOT NULL DEFAULT FALSE;

-- 같은 카테고리 안에서도 실제 매장(지도에서 검색 가능)과 온라인 전용 가맹점이 섞여 있을 수 있다
-- (예: 영화관 카테고리의 CGV/메가박스/롯데시네마는 실제 매장이 있지만, YES24/인터파크는 온라인 예매
-- 채널이라 매장이 없다). 카테고리별 가맹점 목록(GET /merchants)과 근처 가맹점 검색의 키워드 검색(2안)
-- 대상은 실제 매장이 있는 가맹점으로만 좁혀야 하므로 플래그를 둔다. 기존 가맹점은 대부분 실제 매장이
-- 있는 브랜드라 기본값 TRUE로 두고, 온라인 전용 가맹점만 데이터로 FALSE 처리한다.
ALTER TABLE merchants
    ADD COLUMN has_physical_location BOOLEAN NOT NULL DEFAULT TRUE;
