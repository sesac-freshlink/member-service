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
    WITHDRAWN_MEMBER(409, "탈퇴한 계정입니다. 다시 가입해 주세요"),
    STORE_NOT_FOUND(404, "점포가 없습니다"),
    STORE_NOT_EDITABLE(409, "등록 중인 점포는 수정할 수 없습니다"),
    STORE_NAME_DUPLICATED(409, "이미 있는 점포명입니다"),
    STORE_IMAGE_KEY_INVALID(400, "이 점포에 발급한 이미지 키가 아닙니다"),
    STORE_IMAGE_TYPE_INVALID(400, "점포 이미지는 JPG나 PNG만 올릴 수 있습니다"),
    OWNER_EMAIL_DUPLICATED(409, "이미 등록된 이메일입니다"),
    STORE_REGISTRATION_FAILED(500, "점포 등록에 실패했습니다. 다시 시도해 주세요"),
    STORE_IMAGE_NOT_CONFIGURED(500, "점포 이미지를 지금 올릴 수 없습니다. 잠시 후 다시 시도해 주세요"),
    // 회원탈퇴 (추가 명세 A6·A7)
    WITHDRAWAL_BLOCKED_ACTIVE_ORDER(409, "진행 중인 주문이 있어 탈퇴할 수 없습니다. 주문이 끝난 뒤 다시 시도해 주세요"),
    WITHDRAWAL_FAILED(502, "탈퇴를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요"),
    WITHDRAWAL_CHECK_UNAVAILABLE(503, "지금은 주문 확인을 할 수 없어 탈퇴할 수 없습니다. 잠시 후 다시 시도해 주세요"),
    // 게시판 공지사항·FAQ (추가 명세 A17)
    NOTICE_NOT_FOUND(404, "공지사항이 없습니다"),
    FAQ_NOT_FOUND(404, "질문이 없습니다"),
    // 홈 메인 배너 (추가 명세 A18)
    BANNER_NOT_FOUND(404, "배너가 없습니다"),
    BANNER_PERIOD_INVALID(400, "노출 시작은 끝보다 빨라야 합니다"),
    BANNER_ORDER_INVALID(400, "지금 있는 배너를 모두 한 번씩 보내 주세요");

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
