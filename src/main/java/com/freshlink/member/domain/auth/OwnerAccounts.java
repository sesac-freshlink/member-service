package com.freshlink.member.domain.auth;

/** 점주 로그인 계정(Cognito). 테스트에서는 목으로 바꾼다 */
public interface OwnerAccounts {

    /** 같은 이메일 계정이 있는지 (미인증 계정 포함) */
    boolean emailExists(String email);

    /**
     * 점주 계정을 만든다: 이메일 인증 완료, custom:storeId, 영구 비밀번호, OWNER 그룹.
     * 같은 이메일 계정이 이미 있으면 BusinessException(OWNER_EMAIL_DUPLICATED).
     * 중간에 실패하면 만든 계정을 지우고 예외를 던진다. 성공하면 sub를 돌려준다
     */
    String createOwner(String email, String password, long storeId);

    /** 계정을 지운다 (등록 실패 정리용) */
    void delete(String email);
}
