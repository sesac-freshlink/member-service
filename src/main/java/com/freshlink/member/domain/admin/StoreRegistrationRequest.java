package com.freshlink.member.domain.admin;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AD-01 요청. 초기 비밀번호는 DB·로그에 남기지 않는다 (toString에서 가린다).
 * 비밀번호 규칙: 8자 이상, 대문자·소문자·숫자·특수문자 각각 포함
 */
public record StoreRegistrationRequest(
        @NotNull @Size(min = 2, max = 30) String name,
        @NotBlank @Size(max = 255) String address,
        @NotNull Region region,
        @NotBlank @Email @Size(max = 255) String ownerEmail,
        @NotNull @Size(max = 128)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "8자 이상, 대문자·소문자·숫자·특수문자를 각각 포함해야 합니다")
        String initialPassword
) {

    public StoreRegistrationRequest {
        name = name == null ? null : name.trim();
        address = address == null ? null : address.trim();
        ownerEmail = ownerEmail == null ? null : ownerEmail.trim();
    }

    @Override
    public String toString() {
        return "StoreRegistrationRequest[name=" + name + ", region=" + region + ", ownerEmail=" + ownerEmail
                + ", initialPassword=***]";
    }
}
