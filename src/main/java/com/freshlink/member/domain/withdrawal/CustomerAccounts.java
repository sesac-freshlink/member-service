package com.freshlink.member.domain.withdrawal;

import java.util.Optional;

/** 탈퇴하는 고객의 Cognito 계정 (추가 명세 A6) */
public interface CustomerAccounts {

    /**
     * sub로 Cognito 계정을 찾아 지운다(이메일·구글 가입 모두). 지운 계정의 이메일을 돌려준다.
     * 계정이 이미 없으면 빈 값. Cognito 오류는 예외
     */
    Optional<String> delete(String sub);
}
