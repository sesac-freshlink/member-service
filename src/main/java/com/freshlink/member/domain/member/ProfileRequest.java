package com.freshlink.member.domain.member;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** MB-02 요청. 이름은 앞뒤 공백을 지운 값으로 검사한다 (compact 생성자) */
public record ProfileRequest(
        @NotNull @Pattern(regexp = MemberRules.NAME_REGEX) String name,
        @NotNull @Pattern(regexp = MemberRules.NICKNAME_REGEX) String nickname,
        @NotNull Region region
) {

    public ProfileRequest {
        name = name == null ? null : name.trim();
        nickname = nickname == null ? null : nickname.trim();
    }
}
