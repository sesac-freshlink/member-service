package com.freshlink.member.domain.store;

/** 점포와 점주 이메일 (관리자 목록용). 등록 중(PENDING) 점포는 ownerEmail이 null일 수 있다 */
public record StoreWithOwner(Store store, String ownerEmail) {
}
