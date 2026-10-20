package com.freshlink.member.domain.member;

import com.freshlink.member.domain.store.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 회원 (표 member, V1). 키는 Cognito sub. 고객은 이름·닉네임·지역, 점주는 이메일만 (표시 이름은 점포명) */
@Entity
@Table(name = "member")
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "name", length = 30)
    private String name;

    @Column(name = "nickname", length = 10)
    private String nickname;

    @Column(name = "nickname_normalized", length = 10, unique = true)
    private String nicknameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", length = 20)
    private Region region;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Member() {
    }

    /** MB-02 고객 프로필. 닉네임은 입력 표기 그대로 두고 비교값은 따로 둔다 */
    public static Member customer(String id, String name, String nickname, Region region) {
        Member m = new Member();
        m.id = id;
        m.role = MemberRole.CUSTOMER;
        m.name = name;
        m.nickname = nickname;
        m.nicknameNormalized = normalizeNickname(nickname);
        m.region = region;
        return m;
    }

    /** 닉네임 비교값: 앞뒤 공백 제거 + 영문 소문자 (Fresh01 = fresh01) */
    public static String normalizeNickname(String nickname) {
        return nickname.trim().toLowerCase(Locale.ROOT);
    }

    public String getId() { return id; }
    public MemberRole getRole() { return role; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getNickname() { return nickname; }
    public String getNicknameNormalized() { return nicknameNormalized; }
    public Region getRegion() { return region; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
