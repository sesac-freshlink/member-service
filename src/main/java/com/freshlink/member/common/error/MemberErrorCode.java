package com.freshlink.member.common.error;

import com.freshlink.common.error.ErrorCode;

/**
 * member-service 오류 목록. 응답 code는 enum 이름이다.
 * 추가할 땐 여기에 한 줄만 넣는다: {@code XXX(404, "메시지")}. 핸들러는 안 건드린다.
 */
public enum MemberErrorCode implements ErrorCode {

    // 이름·상태·문구는 member-service API 명세 5장과 같다
    FORBIDDEN(403, "권한이 없습니다"),
    MEMBER_PROFILE_NOT_FOUND(404, "회원 정보가 없습니다"),
    NICKNAME_DUPLICATED(409, "이미 사용 중인 닉네임입니다"),
    EMAIL_SIGNUP_EXISTS(409, "이미 이메일로 가입한 계정이 있습니다. 이메일로 로그인해 주세요"),
    STORE_NOT_FOUND(404, "점포가 없습니다"),
    STORE_NOT_EDITABLE(409, "등록 중인 점포는 수정할 수 없습니다"),
    STORE_NAME_DUPLICATED(409, "이미 있는 점포명입니다"),
    OWNER_EMAIL_DUPLICATED(409, "이미 등록된 이메일입니다"),
    STORE_REGISTRATION_FAILED(500, "점포 등록에 실패했습니다. 다시 시도해 주세요");

    private final int status;
    private final String message;

    MemberErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
