package com.freshlink.member.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

    boolean existsByNicknameNormalized(String nicknameNormalized);

    /** MB-04: 이 닉네임을 나 말고 다른 회원이 쓰는지 */
    boolean existsByNicknameNormalizedAndIdNot(String nicknameNormalized, String id);

    boolean existsByEmail(String email);
}
