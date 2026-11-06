package com.freshlink.member.domain.withdrawal;

import com.freshlink.member.domain.member.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 탈퇴 이력 (표 member_withdrawal, V4, 추가 명세 A7). 30일 보관 */
@Entity
@Table(name = "member_withdrawal")
public class MemberWithdrawal {

    /** 본인 탈퇴 / 관리자 삭제 (관리자 삭제는 A8·A9 때) */
    public enum By { SELF, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, length = 36)
    private String memberId;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawn_by", nullable = false, length = 10)
    private By withdrawnBy;

    @Column(name = "withdrawn_at", nullable = false)
    private OffsetDateTime withdrawnAt;

    protected MemberWithdrawal() {
    }

    public static MemberWithdrawal self(String memberId, String email, MemberRole role, OffsetDateTime at) {
        MemberWithdrawal w = new MemberWithdrawal();
        w.memberId = memberId;
        w.email = email;
        w.role = role;
        w.withdrawnBy = By.SELF;
        w.withdrawnAt = at;
        return w;
    }

    public Long getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getEmail() { return email; }
    public MemberRole getRole() { return role; }
    public By getWithdrawnBy() { return withdrawnBy; }
    public OffsetDateTime getWithdrawnAt() { return withdrawnAt; }
}
