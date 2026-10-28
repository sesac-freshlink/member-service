package com.freshlink.member.domain.admin;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AD-01 요청. 초기 비밀번호는 DB·로그에 남기지 않는다 (toString에서 가린다).
 * 비밀번호 규칙: 8자 이상, 대문자·소문자·숫자·특수문자 각각 포함 (dev User Pool 비밀번호 정책과 같다).
 * 특수문자는 Cognito가 특수문자로 세는 ASCII 기호만 인정한다. 한글 같은 문자를 특수문자로 치면 Cognito에서 거절돼 500이 된다.
 * 이메일은 소문자로 바꾼다 (User Pool 사용자 이름은 대소문자를 구분하지 않는다)
 */
public record StoreRegistrationRequest(
        @NotNull(message = "필수입니다") @Size(min = 2, max = 30, message = "2~30자여야 합니다") String name,
        @NotBlank(message = "필수입니다") @Size(max = 255, message = "255자 이하여야 합니다") String address,
        @NotNull(message = "필수입니다") Region region,
        @NotBlank(message = "필수입니다") @Email(message = "이메일 형식이어야 합니다")
        @Size(max = 255, message = "255자 이하여야 합니다") String ownerEmail,
        @NotNull(message = "필수입니다") @Size(max = 128, message = "128자 이하여야 합니다")
        @Pattern(regexp = PASSWORD_REGEX,
                message = "8자 이상, 대문자·소문자·숫자·특수문자를 각각 포함해야 합니다")
        String initialPassword
) {

    /** Cognito가 특수문자로 세는 기호: ^ $ * . [ ] { } ( ) ? " ! @ # % & / \ , > < ' : ; | _ ~ ` = + - 와 공백 */
    static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\^$*.\\[\\]{}()?\"!@#%&/\\\\,><':;|_~`=+\\- ]).{8,}$";

    public StoreRegistrationRequest {
        name = name == null ? null : name.trim();
        address = address == null ? null : address.trim();
        ownerEmail = ownerEmail == null ? null : ownerEmail.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return "StoreRegistrationRequest[name=" + name + ", region=" + region + ", ownerEmail=" + ownerEmail
                + ", initialPassword=***]";
    }
}
