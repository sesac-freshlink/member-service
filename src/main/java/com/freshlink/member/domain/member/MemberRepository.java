package com.freshlink.member.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

    boolean existsByNicknameNormalized(String nicknameNormalized);
}
