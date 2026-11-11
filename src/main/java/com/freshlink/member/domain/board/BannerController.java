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
 * BN-01 홈 메인 배너 (추가 명세 A18). 로그인한 고객만(11/11 경우 결정 1·2). gateway 길 /api/members/**(로그인 필요)를 그대로 쓴다.
 * 나중에 고객 홈을 로그인 없이 열면 gateway 길만 바꾸고 응답 모양은 그대로 둔다
 */
@RestController
@RequestMapping("/members")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping("/banners")
    public List<HomeBanner> banners(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        if (!AuthHeaders.hasRole(role, AuthHeaders.ROLE_CUSTOMER)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
        return bannerService.showing();
    }
}
