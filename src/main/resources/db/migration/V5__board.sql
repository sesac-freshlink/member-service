-- 게시판: 공지사항·FAQ (11/6, 추가 명세 A17). 관리자가 쓰고, 로그인한 고객·점주가 읽는다.
-- audience: ALL(모두) / CUSTOMER(고객만) / OWNER(점주만). 지우면 행을 지운다(이력 없음)
CREATE TABLE notice (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(100)  NOT NULL,
    content     TEXT          NOT NULL COMMENT '본문 (5000자까지, 줄바꿈 그대로)',
    audience    VARCHAR(10)   NOT NULL COMMENT 'ALL / CUSTOMER / OWNER',
    pinned      BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '목록 맨 위 고정',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notice_list (audience, pinned, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE faq (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    question    VARCHAR(200)  NOT NULL,
    answer      TEXT          NOT NULL COMMENT '답 (2000자까지)',
    audience    VARCHAR(10)   NOT NULL COMMENT 'ALL / CUSTOMER / OWNER',
    sort_order  INT           NOT NULL DEFAULT 0 COMMENT '작을수록 위',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_faq_list (audience, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
