package com.freshlink.member.domain.auth;

/** 구글 로그인 계정(Cognito 외부 IdP 사용자). 테스트에서는 목으로 바꾼다 */
public interface SocialAccounts {

    /**
     * 이 sub가 구글로 처음 들어온 계정이고, 같은 이메일로 이메일 가입한 계정이 따로 있으면 true.
     * Cognito에서는 둘이 다른 사용자(다른 sub)로 생긴다. 이을지 막을지 중 막기로 정했다 (BFF 인수인계 6장, 10/29)
     */
    boolean emailSignupExistsFor(String sub);
}
