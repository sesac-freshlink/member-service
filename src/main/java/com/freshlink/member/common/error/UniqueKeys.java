package com.freshlink.member.common.error;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * UNIQUE 충돌을 업무 오류로 바꿀 때 어느 키가 충돌했는지 확인한다.
 * MySQL 메시지는 "Duplicate entry '…' for key '표.키이름'" 모양이다. 다른 무결성 오류(NOT NULL, 길이 초과 등)는 업무 오류로 바꾸지 않는다
 */
public final class UniqueKeys {

    public static final String MEMBER_NICKNAME = "uk_member_nickname_normalized";
    public static final String STORE_NAME = "uk_store_name";

    private UniqueKeys() {
    }

    public static boolean isViolation(DataIntegrityViolationException e, String keyName) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String message = cause.getMessage();
        return message != null && message.contains("Duplicate entry") && message.contains(keyName);
    }
}
