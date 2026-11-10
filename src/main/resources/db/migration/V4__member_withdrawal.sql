-- 회원탈퇴 이력 (11/6, 추가 명세 A6·A7). 탈퇴하면 member 행은 지우고 여기에 남긴다.
-- 이메일은 UNIQUE를 걸지 않는다(같은 이메일로 여러 번 가입·탈퇴할 수 있다). 거주 지역은 보관하지 않는다.
-- 보관 기간은 개인정보 처리방침 3장대로 30일이고, 지난 행은 서비스가 하루 한 번 지운다
CREATE TABLE member_withdrawal (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    member_id     VARCHAR(36)  NOT NULL COMMENT '탈퇴한 회원 ID (Cognito sub). 재가입 계정과 연결하지 않는다',
    email         VARCHAR(255) NULL     COMMENT '탈퇴 때 Cognito에서 읽은 이메일 (없으면 NULL)',
    role          VARCHAR(20)  NOT NULL COMMENT '탈퇴 전 역할',
    withdrawn_by  VARCHAR(10)  NOT NULL COMMENT 'SELF 본인 탈퇴 / ADMIN 관리자 삭제',
    withdrawn_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_member_withdrawal_withdrawn_at (withdrawn_at),
    KEY idx_member_withdrawal_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
