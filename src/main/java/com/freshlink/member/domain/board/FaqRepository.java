package com.freshlink.member.domain.board;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    /** 그 대상들의 FAQ, 순서 → 먼저 쓴 순 */
    List<Faq> findByAudienceInOrderBySortOrderAscIdAsc(Collection<Audience> audiences);

    List<Faq> findAllByOrderBySortOrderAscIdAsc();
}
