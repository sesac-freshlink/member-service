package com.freshlink.member.domain.owner;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * OW-02 요청. 점주는 설명·이미지 키만 보낸다. 이름·주소·시도·좌표 칸은 관리자만 바꾸므로(AD-03),
 * 받아서 하나라도 오면 400으로 막는다(모르는 칸으로 조용히 무시하지 않으려고 칸을 둔다)
 */
public record OwnerStoreUpdateRequest(
        String name,
        String address,
        Region region,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 500, message = "500자 이하여야 합니다") String description,
        String imageKey
) {

    public OwnerStoreUpdateRequest {
        description = description == null ? null : description.trim();
    }

    @AssertTrue(message = "점주는 설명·이미지만 바꿀 수 있습니다")
    public boolean isOwnerFieldsOnly() {
        return name == null && address == null && region == null && latitude == null && longitude == null;
    }

    @AssertTrue(message = "바꿀 필드가 하나도 없습니다")
    public boolean isAnyField() {
        return description != null || imageKey != null;
    }
}
