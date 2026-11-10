package com.freshlink.member.domain.board;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 그 대상들의 공지, 정렬은 pageable (고정 먼저 → 최신순) */
    Page<Notice> findByAudienceIn(Collection<Audience> audiences, Pageable pageable);
}
