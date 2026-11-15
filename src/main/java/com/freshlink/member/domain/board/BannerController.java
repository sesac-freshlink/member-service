package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import com.freshlink.member.domain.board.BannerResponses.HomeBanner;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BN-01 홈 메인 배너 (추가 명세 A18). 고객 홈을 로그인 없이 열면서(11/15 경우 결정) gateway가 GET /api/members/banners 를 공개로 넘긴다.
 * 로그인하지 않은 사람(역할 헤더 없음)과 고객은 보고, 점주·관리자는 403. 응답 모양은 그대로다
 */
@RestController
@RequestMapping("/members")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    /** BN-01 홈 배너 목록. 점주·관리자면 403, 로그인하지 않았거나 고객이면 지금 홈에 보일 배너를 최대 5장 준다 */
    @GetMapping("/banners")
    public List<HomeBanner> banners(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        boolean guest = role == null || role.isBlank();
        if (!guest && !AuthHeaders.hasRole(role, AuthHeaders.ROLE_CUSTOMER)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
        return bannerService.showing();
    }
}
