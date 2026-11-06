package com.freshlink.member.domain.withdrawal;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberWithdrawalRepository extends JpaRepository<MemberWithdrawal, Long> {

    /** 보관 기간이 지난 탈퇴 이력을 지운다. 지운 행 수 */
    @Modifying
    @Query("delete from MemberWithdrawal w where w.withdrawnAt < :before")
    int deleteOlderThan(@Param("before") OffsetDateTime before);
}
