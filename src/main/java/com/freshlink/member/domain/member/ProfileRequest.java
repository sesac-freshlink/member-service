package com.freshlink.member.domain.member;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * MB-02 요청. 이름은 앞뒤 공백을 지운 값으로 검사한다 (compact 생성자).
 * 필수 약관(이용약관·개인정보 처리방침) 두 개에 모두 동의해야 한다 (추가 명세 A16)
 */
public record ProfileRequest(
        @NotNull(message = "필수입니다") @Pattern(regexp = MemberRules.NAME_REGEX, message = MemberRules.NAME_MESSAGE) String name,
        @NotNull(message = "필수입니다")
        @Pattern(regexp = MemberRules.NICKNAME_REGEX, message = MemberRules.NICKNAME_MESSAGE) String nickname,
        @NotNull(message = "필수입니다") Region region,
        Boolean termsAgreed,
        Boolean privacyAgreed
) {

    public ProfileRequest {
        name = name == null ? null : name.trim();
        nickname = nickname == null ? null : nickname.trim();
    }

    @AssertTrue(message = "필수 약관에 동의해 주세요")
    public boolean isRequiredAgreed() {
        return Boolean.TRUE.equals(termsAgreed) && Boolean.TRUE.equals(privacyAgreed);
    }
}
