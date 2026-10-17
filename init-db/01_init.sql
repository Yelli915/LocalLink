-- 온라인 프로그램 플랫폼 초기 DDL
-- Spring JPA ddl-auto: update 로도 생성되지만
-- 명시적 DDL로 테이블 선후 관계를 문서화

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(20)     NOT NULL COMMENT 'MERCHANT | GOV_OFFICER',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 초기 로그인 계정 (비밀번호 평문: 12345678 — auth-server가 BCrypt로 검증하므로 해시로 저장)
INSERT INTO users (email, password, name, role, created_at, updated_at)
VALUES
    ('user1@example.com', '$2a$10$DnoMtIPnQzaN.9BXpLSiEOYsRoUTlS1ThImNeVpXqvGMl495FTybS', '소상공인 1', 'MERCHANT', NOW(6), NOW(6)),
    ('user2@example.com', '$2a$10$DnoMtIPnQzaN.9BXpLSiEOYsRoUTlS1ThImNeVpXqvGMl495FTybS', '소상공인 2', 'MERCHANT', NOW(6), NOW(6)),
    ('user3@example.com', '$2a$10$DnoMtIPnQzaN.9BXpLSiEOYsRoUTlS1ThImNeVpXqvGMl495FTybS', '소상공인 3', 'MERCHANT', NOW(6), NOW(6)),
    ('user4@example.com', '$2a$10$DnoMtIPnQzaN.9BXpLSiEOYsRoUTlS1ThImNeVpXqvGMl495FTybS', '소상공인 4', 'MERCHANT', NOW(6), NOW(6)),
    ('user5@example.com', '$2a$10$DnoMtIPnQzaN.9BXpLSiEOYsRoUTlS1ThImNeVpXqvGMl495FTybS', '소상공인 5', 'MERCHANT', NOW(6), NOW(6)),
    ('admin@example.com', '$2a$10$DnoMtIPnQzaN.9BXpLSiEOYsRoUTlS1ThImNeVpXqvGMl495FTybS', '지자체 관리자', 'GOV_OFFICER', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    name = VALUES(name),
    role = VALUES(role),
    updated_at = VALUES(updated_at);

-- 지자체 담당자가 프로그램 개설 (officer_id → users.id)
CREATE TABLE IF NOT EXISTS programs (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    title            VARCHAR(255)    NOT NULL,
    description      TEXT,
    category         VARCHAR(50)     NOT NULL COMMENT 'SAME_DAY|REGULAR_BUNDLE|COLD_CHAIN|TRADITIONAL_MARKET|LOCAL_COMMERCE|SAFE_DELIVERY|SHARED_STORAGE|OTHER',
    price            DECIMAL(10,2)   NOT NULL,
    officer_id    BIGINT          NOT NULL,
    participation_count INT             NOT NULL DEFAULT 0,
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (officer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 참여자가 참여 신청 (user_id → users.id, program_id → programs.id)
CREATE TABLE IF NOT EXISTS participations (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    program_id   BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | ACTIVE | CANCELLED | FAILED',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_program (user_id, program_id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (program_id) REFERENCES programs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 참여 확정 후 정산 (user_id → users.id, program_id → programs.id)
CREATE TABLE IF NOT EXISTS settlements (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    program_id       BIGINT          NOT NULL,
    amount          DECIMAL(10,2)   NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | COMPLETED | FAILED | CANCELLED',
    transaction_id  VARCHAR(255)    UNIQUE,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (program_id) REFERENCES programs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 물류 업체별 지원 프로그램 목업 데이터
INSERT INTO programs (
    id, title, description, category, price, officer_id,
    participation_count, status, created_at, updated_at
)
VALUES
    (1, '전통시장 당일 공동배송', '중앙물류협동조합이 전통시장 주문을 모아 하루 두 차례 공동 배송한다.', 'SAME_DAY', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (2, '소상공인 정기 묶음배송', '한빛로지스가 여러 점포의 소량 주문을 권역별로 묶어 정기 배송한다.', 'REGULAR_BUNDLE', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (3, '골목상권 공동 보관 지원', '도담공유창고가 소상공인에게 필요한 만큼의 공간과 입출고 관리를 제공한다.', 'COLD_CHAIN', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (4, '계절 상품 수요관리 지원', '상권수요랩이 판매 이력과 계절 정보를 토대로 적정 재고를 제안한다.', 'TRADITIONAL_MARKET', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (5, '우리동네 근거리 배송', '동네한바퀴가 반경 5km 이내 주문을 접수 당일 배송한다.', 'LOCAL_COMMERCE', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (6, '냉장·안심 특수배송', '안심콜드체인이 신선식품과 냉장 상품을 온도 관리 차량으로 배송한다.', 'SAFE_DELIVERY', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (7, '[모아배송] 상점 묶음배송 정기 지원', '주문량이 적은 점포들의 물량을 모아 배송비를 줄인다.', 'SAME_DAY', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (8, '[바로물류] 권역별 공동배송', '주문을 권역별로 자동 분류해 공동 배송 차량에 배차한다.', 'SAME_DAY', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (9, '[팩앤고] 친환경 포장·출고 지원', '상품 검수부터 친환경 포장, 송장 출력, 출고까지 지원한다.', 'REGULAR_BUNDLE', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (10, '[출고친구] 포장·송장 출력 대행', '온라인 주문이 몰리는 시간대에 포장 인력과 출고 공간을 지원한다.', 'REGULAR_BUNDLE', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (11, '[모아풀필먼트] 소형 점포 풀필먼트', '입고, 보관, 피킹, 포장, 출고를 지역 거점에서 통합 처리한다.', 'COLD_CHAIN', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (12, '[새벽창고] 성수기 임시 보관 지원', '명절과 행사 기간에 필요한 임시 보관 공간을 소량부터 제공한다.', 'COLD_CHAIN', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (13, '[수요봄AI] 판매 데이터 기반 재고 알림', '판매 추이를 분석해 재고 부족과 과잉 가능성을 미리 알린다.', 'TRADITIONAL_MARKET', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (14, '[팔림새] 적정 발주량 추천', '품목별 주문 추이를 바탕으로 다음 영업 주기의 준비 수량을 제안한다.', 'TRADITIONAL_MARKET', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (15, '[동네퀵] 반경 5km 당일배송', '시장과 주거 지역을 연결하는 소상공인 전용 근거리 배송을 제공한다.', 'LOCAL_COMMERCE', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (16, '[콜드웨이] 냉장·냉동 안심배송', '신선식품을 상품별 적정 온도로 보관하며 안전하게 운송한다.', 'SAFE_DELIVERY', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (17, '[오더브릿지] 온라인 주문 자동 연동', '여러 판매 채널의 주문을 모아 송장과 배송 요청으로 연결한다.', 'SHARED_STORAGE', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (18, '[상점톡] 다채널 주문 수집 지원', '판매처별 주문을 한 곳에 모아 출고 누락과 중복 작업을 줄인다.', 'SHARED_STORAGE', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6)),
    (19, '[지역물류지원센터] 배송비 지원', '물류 취약 지역과 도서·산간 배송의 추가 비용을 지원한다.', 'OTHER', 99000.00, (SELECT id FROM users WHERE email = 'admin@example.com'), 0, 'ACTIVE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    category = VALUES(category),
    price = VALUES(price),
    officer_id = VALUES(officer_id),
    status = VALUES(status),
    updated_at = VALUES(updated_at);

-- 소상공인 신청 이력: 카테고리별 추천을 확인할 수 있도록 구성
INSERT INTO participations (user_id, program_id, status, created_at, updated_at)
VALUES
    ((SELECT id FROM users WHERE email = 'user1@example.com'), 1, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user1@example.com'), 7, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user1@example.com'), 9, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user2@example.com'), 1, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user2@example.com'), 2, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user2@example.com'), 9, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user3@example.com'), 3, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user3@example.com'), 4, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user3@example.com'), 11, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user4@example.com'), 3, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user4@example.com'), 4, 'ACTIVE', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user4@example.com'), 13, 'ACTIVE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    updated_at = VALUES(updated_at);

-- 위 ACTIVE 신청과 연결된 정산 완료 목업 데이터
INSERT INTO settlements (
    user_id, program_id, amount, status, transaction_id, created_at, updated_at
)
VALUES
    ((SELECT id FROM users WHERE email = 'user1@example.com'), 1, 99000.00, 'COMPLETED', 'SEED-U1-C1', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user1@example.com'), 7, 99000.00, 'COMPLETED', 'SEED-U1-C7', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user1@example.com'), 9, 99000.00, 'COMPLETED', 'SEED-U1-C9', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user2@example.com'), 1, 99000.00, 'COMPLETED', 'SEED-U2-C1', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user2@example.com'), 2, 99000.00, 'COMPLETED', 'SEED-U2-C2', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user2@example.com'), 9, 99000.00, 'COMPLETED', 'SEED-U2-C9', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user3@example.com'), 3, 99000.00, 'COMPLETED', 'SEED-U3-C3', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user3@example.com'), 4, 99000.00, 'COMPLETED', 'SEED-U3-C4', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user3@example.com'), 11, 99000.00, 'COMPLETED', 'SEED-U3-C11', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user4@example.com'), 3, 99000.00, 'COMPLETED', 'SEED-U4-C3', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user4@example.com'), 4, 99000.00, 'COMPLETED', 'SEED-U4-C4', NOW(6), NOW(6)),
    ((SELECT id FROM users WHERE email = 'user4@example.com'), 13, 99000.00, 'COMPLETED', 'SEED-U4-C13', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    program_id = VALUES(program_id),
    amount = VALUES(amount),
    status = VALUES(status),
    updated_at = VALUES(updated_at);

-- ACTIVE 신청 건수와 프로그램의 참여 점포 수를 맞춤
UPDATE programs c
SET c.participation_count = (
    SELECT COUNT(*)
    FROM participations e
    WHERE e.program_id = c.id
      AND e.status = 'ACTIVE'
)
WHERE c.id BETWEEN 1 AND 19;
