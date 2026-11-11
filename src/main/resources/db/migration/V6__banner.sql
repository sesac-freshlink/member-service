-- 홈 메인 배너 (11/11, 추가 명세 A18). 관리자가 쓰고, 로그인한 고객이 읽는다.
-- image_url 은 같은 사이트 경로(지금 /banners/xxx.jpg, S3 연결 뒤 /images/banners/{uuid}.jpg)
-- 연결한 공지를 지우면 배너는 남고 연결만 끊긴다(ON DELETE SET NULL)
CREATE TABLE banner (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(50)   NOT NULL COMMENT '배너 이름 (그림 위에 쓰지 않는다. 대체 텍스트·관리자 목록용)',
    image_url   VARCHAR(255)  NOT NULL COMMENT '같은 사이트 경로',
    notice_id   BIGINT        NULL     COMMENT '누르면 열 공지. 없으면 NULL',
    sort_order  INT           NOT NULL DEFAULT 0 COMMENT '작을수록 앞',
    visible     BOOLEAN       NOT NULL DEFAULT TRUE,
    starts_at   DATETIME(6)   NULL     COMMENT '노출 시작. NULL이면 바로',
    ends_at     DATETIME(6)   NULL     COMMENT '노출 끝(이 시각부터 안 보임). NULL이면 계속',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_banner_list (visible, sort_order),
    CONSTRAINT fk_banner_notice FOREIGN KEY (notice_id) REFERENCES notice (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
