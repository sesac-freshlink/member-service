package com.freshlink.member.domain.store;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;

    public StoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /** ST-01: 지역의 ACTIVE 점포. 검색어가 비면 지역 전체, 있으면 점포명·주소 일부 (대소문자 구분 없음, %·_는 글자 그대로) */
    @Transactional(readOnly = true)
    public List<Store> search(Region region, String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return storeRepository.findByRegionAndStatusOrderByNameAsc(region, StoreStatus.ACTIVE);
        }
        return storeRepository.search(region, StoreStatus.ACTIVE, "%" + escapeLike(trimmed) + "%");
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
