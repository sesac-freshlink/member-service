package com.freshlink.member.domain.board;

import java.util.EnumSet;
import java.util.Set;

/** 게시글을 볼 사람 (추가 명세 A17) */
public enum Audience {
    ALL, CUSTOMER, OWNER;

    /** 그 역할이 볼 수 있는 대상들. 고객은 ALL·CUSTOMER, 점주는 ALL·OWNER */
    public static Set<Audience> visibleTo(boolean owner) {
        return owner ? EnumSet.of(ALL, OWNER) : EnumSet.of(ALL, CUSTOMER);
    }
}
