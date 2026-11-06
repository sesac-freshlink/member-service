package com.freshlink.member.domain.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 관리자 공지 쓰기·고치기. 모든 칸을 보낸다. 제목·본문은 앞뒤 공백(전각 공백 같은 유니코드 공백 포함)을 지운 뒤 검사한다 */
public record NoticeRequest(
        @NotBlank(message = "필수입니다") @Size(max = 100, message = "100자 이하여야 합니다") String title,
        @NotBlank(message = "필수입니다") @Size(max = 5000, message = "5000자 이하여야 합니다") String content,
        @NotNull(message = "필수입니다") Audience audience,
        Boolean pinned
) {

    public NoticeRequest {
        title = title == null ? null : title.strip();
        content = content == null ? null : content.strip();
    }

    public boolean pinnedOrFalse() {
        return Boolean.TRUE.equals(pinned);
    }
}
