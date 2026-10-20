package com.freshlink.member.domain.store;

import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 고객 점포 검색. 공개 API (gateway가 토큰 없이 통과시킨다) */
@RestController
@RequestMapping("/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /** ST-01 점포 검색: region 없으면 SEOUL, keyword는 점포명·주소 일부. 점포명순, 페이지 없음 */
    @GetMapping
    public List<StoreSearchResponse> search(
            @RequestParam(required = false, defaultValue = "SEOUL") Region region,
            @RequestParam(required = false) @Size(max = 50) String keyword) {
        return storeService.search(region, keyword).stream().map(StoreSearchResponse::from).toList();
    }
}
