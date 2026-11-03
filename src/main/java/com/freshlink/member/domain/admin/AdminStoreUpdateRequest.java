package com.freshlink.member.domain.admin;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * AD-03 요청. 보낸 필드만 바꾼다. 점포명은 앞뒤 공백을 지운 뒤 2~30자, 주소는 비어 있으면 안 된다.
 * 점포 상세 값(협의안 ⑤): 위도·경도(소수 6자리), 설명(500자), 이미지 키(업로드 주소 발급에서 받은 키)
 */
public record AdminStoreUpdateRequest(
        @Size(min = 2, max = 30, message = "2~30자여야 합니다") String name,
        @Size(max = 255, message = "255자 이하여야 합니다") @Pattern(regexp = ".*\\S.*", message = "비어 있으면 안 됩니다") String address,
        Region region,
        @DecimalMin(value = "-90", message = "-90~90이어야 합니다") @DecimalMax(value = "90", message = "-90~90이어야 합니다")
        @Digits(integer = 3, fraction = 6, message = "소수 6자리까지입니다") BigDecimal latitude,
        @DecimalMin(value = "-180", message = "-180~180이어야 합니다") @DecimalMax(value = "180", message = "-180~180이어야 합니다")
        @Digits(integer = 3, fraction = 6, message = "소수 6자리까지입니다") BigDecimal longitude,
        @Size(max = 500, message = "500자 이하여야 합니다") String description,
        String imageKey
) {

    public AdminStoreUpdateRequest {
        name = name == null ? null : name.trim();
        address = address == null ? null : address.trim();
        description = description == null ? null : description.trim();
    }

    @AssertTrue(message = "바꿀 필드가 하나도 없습니다")
    public boolean isAnyField() {
        return name != null || address != null || region != null
                || latitude != null || longitude != null || description != null || imageKey != null;
    }
}
