package com.freshlink.member.domain.store;

/** ST-01 점포 검색 한 줄. 상태·점주 정보는 내리지 않는다 */
public record StoreSearchResponse(Long storeId, String storeCode, String name, String address, Region region) {

    public static StoreSearchResponse from(Store store) {
        return new StoreSearchResponse(store.getId(), store.getStoreCode(), store.getName(), store.getAddress(),
                store.getRegion());
    }
}
