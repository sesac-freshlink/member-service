package com.freshlink.member.domain.member;

/** MB-01 응답. nickname은 받은 값 그대로 */
public record NicknameAvailabilityResponse(String nickname, boolean available) {
}
