package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import com.freshlink.member.domain.board.BannerResponses.AdminBanner;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 메인배너 (추가 명세 A18, AD-13~AD-17). gateway가 /api/admin/** 를 ADMIN만 통과시키고 여기서도 한 번 더 본다 */
@RestController
@RequestMapping("/admin/banners")
public class AdminBannerController {

    private final BannerService bannerService;

    public AdminBannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    /** AD-13 모든 배너를 순서대로 준다 (꺼 둔 것·기간 밖 포함) */
    @GetMapping
    public List<AdminBanner> list(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        requireAdmin(role);
        return bannerService.all();
    }

    /** AD-14 배너를 올린다. 입력이 틀리면 400, 연결할 공지가 없으면 404 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminBanner create(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                              @Valid @RequestBody BannerRequest request) {
        requireAdmin(role);
        return bannerService.create(request);
    }

    /** AD-15 배너의 모든 칸을 바꾼다. 없는 배너면 404 */
    @PutMapping("/{bannerId}")
    public AdminBanner update(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                              @PathVariable Long bannerId, @Valid @RequestBody BannerRequest request) {
        requireAdmin(role);
        return bannerService.update(bannerId, request);
    }

    /** AD-16 배너를 바로 지운다. 없는 배너면 404 */
    @DeleteMapping("/{bannerId}")
    public ResponseEntity<Void> delete(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                       @PathVariable Long bannerId) {
        requireAdmin(role);
        bannerService.delete(bannerId);
        return ResponseEntity.noContent().build();
    }

    /** AD-17 지금 있는 배너 전부를 받은 순서대로 0부터 다시 매긴다 */
    @PutMapping("/order")
    public List<AdminBanner> reorder(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                     @Valid @RequestBody BannerOrderRequest request) {
        requireAdmin(role);
        return bannerService.reorder(request.bannerIds());
    }

    /** 관리자가 아니면 403을 던진다 (gateway가 먼저 막지만 한 번 더 본다) */
    private static void requireAdmin(String role) {
        if (!AuthHeaders.hasRole(role, AuthHeaders.ROLE_ADMIN)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
    }
}
