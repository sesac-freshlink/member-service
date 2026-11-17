package com.freshlink.member.domain.member;

import io.swagger.v3.oas.annotations.media.Schema;

/** MB-01 응답. nickname은 받은 값 그대로 */
@Schema(description = "MB-01 응답. nickname은 받은 값 그대로")
public record NicknameAvailabilityResponse(String nickname, boolean available) {
}
