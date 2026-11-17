package com.freshlink.member.domain.board;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /** 홈에 보일 배너: 보이기 켜짐 + 지금이 기간 안(비면 제한 없음), 순서 → 먼저 만든 것 */
    @Query("""
            SELECT b FROM Banner b
            WHERE b.visible = true
              AND (b.startsAt IS NULL OR b.startsAt <= :now)
              AND (b.endsAt IS NULL OR b.endsAt > :now)
            ORDER BY b.sortOrder ASC, b.id ASC
            """)
    List<Banner> findShowing(@Param("now") OffsetDateTime now, Limit limit);

    List<Banner> findAllByOrderBySortOrderAscIdAsc();

    /** 고치기·지우기: 같은 배너를 동시에 바꾸지 않게 줄을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Banner b WHERE b.id = :id")
    Optional<Banner> findForUpdate(@Param("id") Long id);

    /** 순서 바꾸기: 지금 있는 배너 전부를 잠근다 (순서 바꾸기 두 번이 섞이지 않게) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Banner b ORDER BY b.id")
    List<Banner> findAllForUpdate();
}
