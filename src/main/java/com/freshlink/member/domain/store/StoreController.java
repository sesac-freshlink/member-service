package com.freshlink.member.domain.store;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 고객 점포 검색·점포 상세. 공개 API (gateway가 토큰 없이 통과시킨다) */
@RestController
@RequestMapping("/stores")
public class StoreController {

    /** 앞뒤 공백을 뺀 길이로 50자까지 (공백만 있으면 지역 전체) */
    static final String KEYWORD_REGEX = "^\\s*.{0,50}\\s*$";

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /** ST-01 점포 검색: region 없으면 SEOUL, keyword는 점포명·주소 일부. 점포명순, 페이지 없음 */
    @GetMapping
    public List<StoreSearchResponse> search(
            @RequestParam(required = false, defaultValue = "SEOUL") Region region,
            @RequestParam(required = false) @Pattern(regexp = KEYWORD_REGEX, message = "앞뒤 공백을 빼고 50자 이하") String keyword) {
        return storeService.search(region, keyword).stream().map(StoreSearchResponse::from).toList();
    }

    /** 점포 상세 (공개): 운영 중인 점포의 이름·주소·지도 좌표·설명·이미지. 없으면 404 STORE_NOT_FOUND */
    @GetMapping("/{storeId}")
    public StoreDetailResponse detail(@PathVariable Long storeId) {
        return StoreDetailResponse.from(storeService.detail(storeId));
    }
}
