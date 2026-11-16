package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import com.freshlink.member.domain.admin.StoreImageUploadUrlRequest;
import com.freshlink.member.domain.board.BannerResponses.AdminBanner;
import com.freshlink.member.domain.store.StoreImageUploads;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final StoreImageUploads imageUploads;

    public AdminBannerController(BannerService bannerService, StoreImageUploads imageUploads) {
        this.bannerService = bannerService;
        this.imageUploads = imageUploads;
    }

    /** AD-13 모든 배너를 순서대로 준다 (꺼 둔 것·기간 밖 포함) */
    @Operation(summary = "AD-13 모든 배너를 순서대로 준다 (꺼 둔 것·기간 밖 포함)")
    @GetMapping
    public List<AdminBanner> list(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        requireAdmin(role);
        return bannerService.all();
    }

    /** AD-14 배너를 올린다. 입력이 틀리면 400, 연결할 공지가 없으면 404 */
    @Operation(summary = "AD-14 배너를 올린다. 입력이 틀리면 400, 연결할 공지가 없으면 404")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminBanner create(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                              @Valid @RequestBody BannerRequest request) {
        requireAdmin(role);
        return bannerService.create(request);
    }

    /** AD-15 배너의 모든 칸을 바꾼다. 없는 배너면 404 */
    @Operation(summary = "AD-15 배너의 모든 칸을 바꾼다. 없는 배너면 404")
    @PutMapping("/{bannerId}")
    public AdminBanner update(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                              @PathVariable Long bannerId, @Valid @RequestBody BannerRequest request) {
        requireAdmin(role);
        return bannerService.update(bannerId, request);
    }

    /** AD-16 배너를 바로 지운다. 없는 배너면 404 */
    @Operation(summary = "AD-16 배너를 바로 지운다. 없는 배너면 404")
    @ApiResponse(responseCode = "204", description = "배너를 지웠다")
    @DeleteMapping("/{bannerId}")
    public ResponseEntity<Void> delete(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                       @PathVariable Long bannerId) {
        requireAdmin(role);
        bannerService.delete(bannerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * AD-18 배너 그림 업로드 주소 발급 (11/16). 점포 이미지 업로드 주소와 같은 방식이다: presigned PUT, 기본 5분(실제 만료 시각은 응답 expiresAt).
     * 본문 {"contentType": "image/jpeg" | "image/png"}이고, 본문이 없거나 contentType이 없으면 image/jpeg. 화면은 uploadUrl로 그림을 올린 뒤
     * imageUrl(/images/banners/{uuid}.jpg)을 AD-14·AD-15 배너 imageUrl에 넣는다
     */
    @Operation(summary = "AD-18 배너 그림 업로드 주소 발급 (presigned PUT, 기본 5분. 실제 만료 시각은 응답 expiresAt). 본문 contentType은 image/jpeg 또는 image/png(없으면 image/jpeg). 올린 뒤 imageUrl을 배너 등록·고치기에 넣는다")
    @PostMapping("/image-upload-url")
    public StoreImageUploads.UploadUrl imageUploadUrl(
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestBody(required = false) StoreImageUploadUrlRequest request) {
        requireAdmin(role);
        return imageUploads.issueBanner(request == null ? null : request.contentType());
    }

    /** AD-17 지금 있는 배너 전부를 받은 순서대로 0부터 다시 매긴다 */
    @Operation(summary = "AD-17 지금 있는 배너 전부를 받은 순서대로 0부터 다시 매긴다")
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
