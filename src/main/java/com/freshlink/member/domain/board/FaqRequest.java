package com.freshlink.member.domain.board;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 관리자 FAQ 쓰기·고치기. 모든 칸을 보낸다. 순서는 0~9999, 작을수록 위 (없으면 0) */
public record FaqRequest(
        @NotBlank(message = "필수입니다") @Size(max = 200, message = "200자 이하여야 합니다") String question,
        @NotBlank(message = "필수입니다") @Size(max = 2000, message = "2000자 이하여야 합니다") String answer,
        @NotNull(message = "필수입니다") Audience audience,
        @Min(value = 0, message = "0~9999여야 합니다") @Max(value = 9999, message = "0~9999여야 합니다") Integer sortOrder
) {

    public FaqRequest {
        question = BoardText.trim(question);
        answer = BoardText.trim(answer);
    }

    public int sortOrderOrZero() {
        return sortOrder == null ? 0 : sortOrder;
    }
}
