package com.freshlink.member.domain.admin;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 점포 API. gateway가 /api/admin/**를 ADMIN만 통과시키고, 여기서도 한 번 더 본다 */
@RestController
@RequestMapping("/admin/stores")
public class AdminStoreController {

    private final AdminStoreService adminStoreService;
    private final StoreRegistrationService storeRegistrationService;

    public AdminStoreController(AdminStoreService adminStoreService, StoreRegistrationService storeRegistrationService) {
        this.adminStoreService = adminStoreService;
        this.storeRegistrationService = storeRegistrationService;
    }

    /** AD-01 점포·점주 등록 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminStoreResponse register(
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @Valid @RequestBody StoreRegistrationRequest request) {
        requireAdmin(role);
        return storeRegistrationService.register(request);
    }

    /** AD-02 점포 목록 (page는 0부터, 크기 20 고정) */
    @GetMapping
    public PageResponse<AdminStoreResponse> list(
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다") int page) {
        requireAdmin(role);
        return adminStoreService.list(page);
    }

    /** AD-03 점포 수정 (보낸 필드만) */
    @PatchMapping("/{storeId}")
    public AdminStoreResponse update(
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @PathVariable Long storeId,
            @Valid @RequestBody AdminStoreUpdateRequest request) {
        requireAdmin(role);
        return adminStoreService.update(storeId, request);
    }

    private static void requireAdmin(String role) {
        if (!AuthHeaders.hasRole(role, AuthHeaders.ROLE_ADMIN)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
    }
}
