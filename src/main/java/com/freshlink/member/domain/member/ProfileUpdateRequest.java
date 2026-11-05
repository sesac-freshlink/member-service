package com.freshlink.member.domain.member;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/**
 * MB-04 요청 (추가 명세 A3). 바꿀 칸만 보낸다. 보낸 칸은 MB-02와 같은 규칙으로 검사하고, 한 칸도 없으면 400.
 * 이메일은 받지 않는다 (수정 불가)
 */
public record ProfileUpdateRequest(
        @Pattern(regexp = MemberRules.NAME_REGEX, message = MemberRules.NAME_MESSAGE) String name,
        @Pattern(regexp = MemberRules.NICKNAME_REGEX, message = MemberRules.NICKNAME_MESSAGE) String nickname,
        Region region
) {

    public ProfileUpdateRequest {
        name = name == null ? null : name.trim();
        nickname = nickname == null ? null : nickname.trim();
    }

    /** 이름·닉네임·지역 중 하나는 보내야 한다 (오류 칸 이름은 anyFieldPresent) */
    @AssertTrue(message = "이름·닉네임·지역 중 하나 이상 보내야 합니다")
    public boolean isAnyFieldPresent() {
        return name != null || nickname != null || region != null;
    }
}
