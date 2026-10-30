package com.freshlink.member.domain.admin;

import com.freshlink.member.domain.store.Region;
import com.freshlink.member.domain.store.Store;
import com.freshlink.member.domain.store.StoreStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** AD-01·02·03 점포 한 줄. createdAt은 +09:00. 점포 상세 값(좌표·설명·이미지 주소)은 아직 없으면 null */
public record AdminStoreResponse(Long storeId, String storeCode, String name, String address, Region region,
                                 StoreStatus status, String ownerEmail, OffsetDateTime createdAt,
                                 BigDecimal latitude, BigDecimal longitude, String description, String imageUrl) {

    public static AdminStoreResponse from(Store s, String ownerEmail) {
        OffsetDateTime createdAt = s.getCreatedAt() == null ? null
                : s.getCreatedAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        return new AdminStoreResponse(s.getId(), s.getStoreCode(), s.getName(), s.getAddress(), s.getRegion(),
                s.getStatus(), ownerEmail, createdAt, s.getLatitude(), s.getLongitude(), s.getDescription(),
                s.imageUrl());
    }
}
