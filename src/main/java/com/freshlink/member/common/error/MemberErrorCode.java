package com.freshlink.member.common.error;

import com.freshlink.common.error.ErrorCode;

/**
 * member-service 오류 목록. 응답 code는 enum 이름이다.
 * 추가할 땐 여기에 한 줄만 넣는다: {@code XXX(404, "메시지")}. 핸들러는 안 건드린다.
 */
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(404, "회원이 없습니다"),
    MEMBER_ALREADY_EXISTS(409, "이미 등록된 회원입니다"),
    STORE_NOT_FOUND(404, "점포가 없습니다"),
    STORE_FORBIDDEN(403, "내 점포가 아닙니다"),
    STORE_CLOSED(409, "영업 중이 아닌 점포입니다"),
    SERVICE_UNAVAILABLE(503, "잠시 후 다시 시도해 주세요");

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
