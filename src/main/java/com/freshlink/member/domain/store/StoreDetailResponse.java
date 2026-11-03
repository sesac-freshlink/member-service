package com.freshlink.member.domain.store;

import java.math.BigDecimal;

/**
 * 점포 상세 (공개). 공개해도 되는 값만 내린다: 이름·주소·시도·지도 좌표·설명·이미지 주소.
 * 상태·점주 정보는 내리지 않는다. 값이 아직 없으면 null이다. imageUrl은 같은 주소의 /images/... 경로다
 */
public record StoreDetailResponse(Long storeId, String storeCode, String name, String address, Region region,
                                  BigDecimal latitude, BigDecimal longitude, String description, String imageUrl) {

    public static StoreDetailResponse from(Store s) {
        return new StoreDetailResponse(s.getId(), s.getStoreCode(), s.getName(), s.getAddress(), s.getRegion(),
                s.getLatitude(), s.getLongitude(), s.getDescription(), s.imageUrl());
    }
}
