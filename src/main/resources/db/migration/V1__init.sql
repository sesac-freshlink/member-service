-- member-service V1: 회원·점포 표 (설계: member·order MVP ERD, 2026-10-15)
-- 스키마: member. 물리 FK는 두지 않고 인덱스만 둔다(inventory와 같은 방식). 연결은 서비스 코드가 지킨다.

CREATE TABLE member (
    id                  VARCHAR(36)  NOT NULL COMMENT 'Cognito sub',
    role                VARCHAR(20)  NOT NULL COMMENT 'CUSTOMER / OWNER',
    email               VARCHAR(255) NULL     COMMENT '점주만 저장 (고객은 NULL)',
    name                VARCHAR(30)  NULL     COMMENT '고객 이름 (점주는 NULL, 표시 이름은 점포명)',
    nickname            VARCHAR(10)  NULL     COMMENT '고객 닉네임, 입력한 표기 그대로',
    nickname_normalized VARCHAR(10)  NULL     COMMENT 'trim + 소문자, 중복 검사용',
    region              VARCHAR(20)  NULL     COMMENT '고객 거주 시·도 코드 (SEOUL…JEJU)',
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_nickname_normalized (nickname_normalized),
    UNIQUE KEY uk_member_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE store (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '점포 ID = Cognito custom:storeId',
    store_code      VARCHAR(20)  NULL     COMMENT 'S + id (예: S1, 0 채움 없음), INSERT 직후 같은 트랜잭션에서 채움',
    name            VARCHAR(30)  NOT NULL COMMENT '점포명 = 점주 표시 이름',
    address         VARCHAR(255) NOT NULL,
    region          VARCHAR(20)  NOT NULL COMMENT '시·도 코드',
    status          VARCHAR(20)  NOT NULL COMMENT 'PENDING / ACTIVE / INACTIVE',
    owner_member_id VARCHAR(36)  NULL     COMMENT '점주 member.id. PENDING만 NULL (CHECK로 보장)',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_code (store_code),
    UNIQUE KEY uk_store_name (name),
    UNIQUE KEY uk_store_owner (owner_member_id),
    KEY idx_store_region_status (region, status),
    KEY idx_store_created_at (created_at),
    CONSTRAINT ck_store_owner_linked CHECK (status = 'PENDING' OR owner_member_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
