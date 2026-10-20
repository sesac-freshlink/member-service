package com.freshlink.member.domain.store;

/** 점포 상태 (표 store.status, V1). 고객 검색은 ACTIVE만 */
public enum StoreStatus {
    /** 등록 중 (점포 ID 확보, 계정·점주 연결 전) */
    PENDING,
    /** 운영 중 */
    ACTIVE,
    /** 운영 중단 (전환 기능은 추후) */
    INACTIVE
}
