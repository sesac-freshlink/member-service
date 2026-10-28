package com.freshlink.member.domain.member;

/** 회원 역할 (표 member.role, V1). ADMIN은 member 행이 없다 (Cognito 그룹만) */
public enum MemberRole {
    CUSTOMER,
    OWNER
}
