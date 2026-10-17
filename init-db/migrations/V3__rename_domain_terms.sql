-- 기존 DB(강의 마켓플레이스 시절 스키마)를 공동물류 도메인 스키마로 바꾸는 수동 마이그레이션.
-- 새로 띄우는 DB 는 01_init.sql 이 이미 이 상태로 만들어 주므로 실행할 필요가 없다.
-- (이 폴더는 docker-entrypoint-initdb.d 가 실행하지 않는다 - 하위 디렉터리는 무시됨)
--
-- 변경 내용
--   테이블   courses → programs, enrollments → participations, payments → settlements
--   컬럼     programs.instructor_id → officer_id, programs.enrollment_count → participation_count,
--            participations.course_id / settlements.course_id → program_id
--   값       users.role  STUDENT → MERCHANT, INSTRUCTOR → GOV_OFFICER
--            programs.category  BACKEND → SAME_DAY, FRONTEND → REGULAR_BUNDLE, DEVOPS → COLD_CHAIN,
--                               DATA_SCIENCE → TRADITIONAL_MARKET, MOBILE → LOCAL_COMMERCE,
--                               SECURITY → SAFE_DELIVERY, DATABASE → SHARED_STORAGE, OTHER 유지
--   상태     participations.status 에 FAILED 허용 (정산 요청 실패 보상 / 재신청)
--
-- 주의
--   * 데이터베이스 이름도 lecture_db → locallink_db 로 바뀌었다. compose 는 새 이름으로 접속하므로
--     기존 볼륨을 그대로 쓰려면 먼저 locallink_db 를 만들고 데이터를 옮겨야 한다.
--       docker exec -i lecturedb mariadb -uroot -p -e "CREATE DATABASE locallink_db"
--       docker exec lecturedb mariadb-dump -uroot -p lecture_db | docker exec -i lecturedb mariadb -uroot -p locallink_db
--     (데이터가 필요 없다면 `docker compose down -v` 후 다시 띄우는 편이 간단하다 - 01_init.sql 이 시드를 넣는다)
--   * 이 스크립트는 locallink_db 에서 한 번만 실행한다. 되돌리는 스크립트는 없다.
--
-- 실행:  docker exec -i <db 컨테이너> mariadb -umanager -p locallink_db < init-db/migrations/V3__rename_domain_terms.sql

-- Hibernate(ddl-auto: update) 가 ENUM 으로 만들어 둔 컬럼은 값 변경이 안 되므로 먼저 VARCHAR 로 통일한다.
ALTER TABLE courses     MODIFY COLUMN category VARCHAR(50) NOT NULL;
ALTER TABLE enrollments MODIFY COLUMN status   VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE users       MODIFY COLUMN role     VARCHAR(20) NOT NULL;

RENAME TABLE courses TO programs, enrollments TO participations, payments TO settlements;

ALTER TABLE programs
    CHANGE COLUMN instructor_id    officer_id          BIGINT NOT NULL,
    CHANGE COLUMN enrollment_count participation_count INT    NOT NULL DEFAULT 0;
ALTER TABLE participations CHANGE COLUMN course_id program_id BIGINT NOT NULL;
ALTER TABLE settlements    CHANGE COLUMN course_id program_id BIGINT NOT NULL;

UPDATE users SET role = 'MERCHANT'    WHERE role = 'STUDENT';
UPDATE users SET role = 'GOV_OFFICER' WHERE role = 'INSTRUCTOR';

UPDATE programs SET category = CASE category
    WHEN 'BACKEND'      THEN 'SAME_DAY'
    WHEN 'FRONTEND'     THEN 'REGULAR_BUNDLE'
    WHEN 'DEVOPS'       THEN 'COLD_CHAIN'
    WHEN 'DATA_SCIENCE' THEN 'TRADITIONAL_MARKET'
    WHEN 'MOBILE'       THEN 'LOCAL_COMMERCE'
    WHEN 'SECURITY'     THEN 'SAFE_DELIVERY'
    WHEN 'DATABASE'     THEN 'SHARED_STORAGE'
    ELSE category
END;

ALTER TABLE participations
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING | ACTIVE | CANCELLED | FAILED';
