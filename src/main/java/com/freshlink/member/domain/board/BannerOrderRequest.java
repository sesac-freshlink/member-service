package com.freshlink.member.domain.board;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 배너 순서 바꾸기 (AD-17). 지금 있는 배너 전부를 원하는 순서로 보낸다 */
public record BannerOrderRequest(
        @NotNull(message = "필수입니다") @Size(max = 1000, message = "너무 많습니다") List<@NotNull(message = "필수입니다") Long> bannerIds
) {
}
