package com.freshlink.member.domain.admin;

import com.freshlink.member.domain.store.Region;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** AD-03 요청. 보낸 필드만 바꾼다. 점포명은 앞뒤 공백을 지운 뒤 2~30자, 주소는 비어 있으면 안 된다 */
public record AdminStoreUpdateRequest(
        @Size(min = 2, max = 30) String name,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "비어 있으면 안 됩니다") String address,
        Region region
) {

    public AdminStoreUpdateRequest {
        name = name == null ? null : name.trim();
        address = address == null ? null : address.trim();
    }

    @AssertTrue(message = "바꿀 필드가 하나도 없습니다")
    public boolean isAnyField() {
        return name != null || address != null || region != null;
    }
}
