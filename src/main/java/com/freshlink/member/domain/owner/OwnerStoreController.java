package com.freshlink.member.domain.owner;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import com.freshlink.member.domain.admin.StoreImageUploadUrlRequest;
import com.freshlink.member.domain.store.StoreImageUploads;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 점주 내 점포 API (추가 명세 A15, OW-01~03). 점포는 X-User-Id(점주 sub)로 찾는다. OWNER만 */
@RestController
@RequestMapping("/owner/store")
public class OwnerStoreController {

    private final OwnerStoreService ownerStoreService;

    public OwnerStoreController(OwnerStoreService ownerStoreService) {
        this.ownerStoreService = ownerStoreService;
    }

    /** OW-01 내 점포 조회 */
    @GetMapping
    public OwnerStoreResponse get(
            @RequestHeader(name = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        requireOwner(userId, role);
        return ownerStoreService.get(userId);
    }

    /** OW-02 내 점포 수정 (설명·이미지만, 보낸 필드만) */
    @PatchMapping
    public OwnerStoreResponse update(
            @RequestHeader(name = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @Valid @RequestBody OwnerStoreUpdateRequest request) {
        requireOwner(userId, role);
        return ownerStoreService.update(userId, request);
    }

    /** OW-03 내 점포 이미지 업로드 주소 (관리자와 같은 presigned PUT 5분). 본문이 없거나 contentType이 없으면 image/jpeg */
    @PostMapping("/image-upload-url")
    public StoreImageUploads.UploadUrl imageUploadUrl(
            @RequestHeader(name = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestBody(required = false) StoreImageUploadUrlRequest request) {
        requireOwner(userId, role);
        return ownerStoreService.imageUploadUrl(userId, request == null ? null : request.contentType());
    }

    private static void requireOwner(String userId, String role) {
        if (!AuthHeaders.hasRole(role, AuthHeaders.ROLE_OWNER) || !AuthHeaders.validUserId(userId)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
    }
}
