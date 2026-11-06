package com.freshlink.member.domain.owner;

import com.freshlink.member.domain.store.Region;
import com.freshlink.member.domain.store.Store;
import com.freshlink.member.domain.store.StoreStatus;
import java.math.BigDecimal;

/** OW-01·02 내 점포. 칸 이름은 관리자·공개 점포 응답과 같다. 값이 아직 없으면 null, imageUrl은 같은 주소의 /images/... 경로다 */
public record OwnerStoreResponse(Long storeId, String name, String address, Region region,
                                 BigDecimal latitude, BigDecimal longitude, String description, String imageUrl,
                                 StoreStatus status) {

    public static OwnerStoreResponse from(Store s) {
        return new OwnerStoreResponse(s.getId(), s.getName(), s.getAddress(), s.getRegion(),
                s.getLatitude(), s.getLongitude(), s.getDescription(), s.imageUrl(), s.getStatus());
    }
}
