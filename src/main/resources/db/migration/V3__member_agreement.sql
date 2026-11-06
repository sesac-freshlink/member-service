-- 회원가입 약관 동의 (11/6, 추가 명세 A16). 고객 프로필 등록(MB-02) 때 동의한 이용약관·개인정보 처리방침 버전과 시각.
-- 버전은 날짜 문자열(예: 2026-11-06). 이미 가입한 회원·점주는 NULL로 둔다(다시 동의받지 않는다)
ALTER TABLE member
    ADD COLUMN terms_version     VARCHAR(10) NULL COMMENT '동의한 이용약관 버전' AFTER region,
    ADD COLUMN terms_agreed_at   DATETIME(6) NULL COMMENT '이용약관 동의 시각' AFTER terms_version,
    ADD COLUMN privacy_version   VARCHAR(10) NULL COMMENT '동의한 개인정보 처리방침 버전' AFTER terms_agreed_at,
    ADD COLUMN privacy_agreed_at DATETIME(6) NULL COMMENT '개인정보 처리방침 동의 시각' AFTER privacy_version;
