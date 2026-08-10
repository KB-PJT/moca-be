-- =====================================================================
-- 한뮤 로컬 사용자용 홈 시뮬레이션 실행 파일
--
-- 저장소 루트에서 MySQL CLI로 실행:
--   mysql -h 127.0.0.1 -P 3306 -u <USER> -p <DATABASE> \
--     < src/test/resources/db/fixture/home-simulation-local-hanmyu.sql
--
-- 아래 SOURCE 명령은 MySQL CLI 기준이다. DataGrip 등에서 실행할 때는
-- 이 파일의 SET 문 4개를 먼저 실행하고 같은 세션에서
-- home-simulation-cardgorilla.sql 전체를 실행한다.
-- =====================================================================

SET @SIMULATION_USER_ID := '37411c29-5adc-4643-8ca5-8fa1c14abf1d';
SET @SIMULATION_GOOGLE_SUBJECT := '110799107250112242891';
SET @SIMULATION_NICKNAME := '한뮤';
SET @SIMULATION_EMAIL := 'hanmyu31@gmail.com';

SOURCE src/test/resources/db/fixture/home-simulation-cardgorilla.sql;
