package com.freshlink.member.domain.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /** 지역·상태의 점포 전체, 점포명순 (인덱스 idx_store_region_status) */
    List<Store> findByRegionAndStatusOrderByNameAsc(Region region, StoreStatus status);

    /** 점포명·주소에 pattern(LIKE, 이스케이프 문자 !)이 들어간 점포, 점포명순 */
    @Query("select s from Store s where s.region = :region and s.status = :status "
            + "and (s.name like :pattern escape '!' or s.address like :pattern escape '!') order by s.name asc")
    List<Store> search(@Param("region") Region region, @Param("status") StoreStatus status,
                       @Param("pattern") String pattern);

    /** 점주의 점포 (owner_member_id는 유니크라 최대 1건) */
    Optional<Store> findByOwnerMemberId(String ownerMemberId);

    boolean existsByNameAndIdNot(String name, Long id);

    /** AD-02: 점포와 점주 이메일(점주가 없으면 null), 등록일 최신순 */
    @Query(value = "select new com.freshlink.member.domain.store.StoreWithOwner(s, m.email) from Store s "
            + "left join com.freshlink.member.domain.member.Member m on m.id = s.ownerMemberId "
            + "order by s.createdAt desc, s.id desc",
            countQuery = "select count(s) from Store s")
    Page<StoreWithOwner> findAllWithOwner(Pageable pageable);
}
