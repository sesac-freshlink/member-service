package com.freshlink.member.domain.member;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freshlink.member.domain.store.Region;
import com.freshlink.member.domain.store.Store;

/**
 * MB-03 응답. 고객은 이름·닉네임·지역, 점주는 이메일·점포(표시 이름은 점포명)·지역.
 * 역할에 없는 칸은 JSON에서 뺀다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(String memberId, MemberRole role, String name, String nickname, String email,
                         Long storeId, String storeCode, String storeName, Region region) {

    public static MeResponse customer(Member m) {
        return new MeResponse(m.getId(), m.getRole(), m.getName(), m.getNickname(), null, null, null, null,
                m.getRegion());
    }

    public static MeResponse owner(Member m, Store store) {
        if (store == null) {
            return new MeResponse(m.getId(), m.getRole(), null, null, m.getEmail(), null, null, null, null);
        }
        return new MeResponse(m.getId(), m.getRole(), null, null, m.getEmail(), store.getId(), store.getStoreCode(),
                store.getName(), store.getRegion());
    }
}
