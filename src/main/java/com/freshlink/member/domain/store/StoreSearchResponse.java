package com.freshlink.member.domain.store;

import java.math.BigDecimal;

/**
 * ST-01 점포 검색 한 줄. 상태·점주 정보는 내리지 않는다.
 * 지도 좌표는 점포 상세(StoreDetailResponse)와 같은 값이고, 아직 없으면 null이다 (11/10 주변 점포 재고가 상세를 여러 번 읽지 않게)
 */
public record StoreSearchResponse(Long storeId, String storeCode, String name, String address, Region region,
                                  BigDecimal latitude, BigDecimal longitude) {

    public static StoreSearchResponse from(Store store) {
        return new StoreSearchResponse(store.getId(), store.getStoreCode(), store.getName(), store.getAddress(),
                store.getRegion(), store.getLatitude(), store.getLongitude());
    }
}
