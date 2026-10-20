package com.freshlink.member.domain.store;

import java.util.List;
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
}
