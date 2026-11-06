package com.freshlink.member.domain.member;

/** 회원 입력 규칙 (API 명세 MB-01·MB-02). 검증 애너테이션과 서비스가 같은 정규식을 쓴다 */
public final class MemberRules {

    /** 이름: 앞뒤 공백 제거 후 2~30자, 한글·영문·공백 */
    public static final String NAME_REGEX = "^[가-힣a-zA-Z ]{2,30}$";
    public static final String NAME_MESSAGE = "2~30자 한글·영문이어야 합니다";
    /** 닉네임: 2~10자, 한글·영문·숫자 (공백·특수문자 불가) */
    public static final String NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,10}$";
    public static final String NICKNAME_MESSAGE = "2~10자 한글·영문·숫자여야 합니다";

    private MemberRules() {
    }

    /** 지금 시행 중인 이용약관·개인정보 처리방침 버전 (추가 명세 A16, 화면 약관 글의 시행일과 같다) */
    public static final String TERMS_VERSION = "2026-11-06";
    public static final String PRIVACY_VERSION = "2026-11-06";
}
