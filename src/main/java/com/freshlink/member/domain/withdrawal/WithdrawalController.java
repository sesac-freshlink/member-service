package com.freshlink.member.domain.withdrawal;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MB-05 고객 회원탈퇴 (추가 명세 A6). 성공 204. 점주·관리자는 이 API로 탈퇴하지 않는다 */
@RestController
@RequestMapping("/members")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @RequestHeader(name = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        // 고객만. 점주·관리자 역할이 같이 있으면(점주 계정이 지워질 수 있다) 막는다
        if (!AuthHeaders.hasRole(role, AuthHeaders.ROLE_CUSTOMER) || AuthHeaders.hasRole(role, AuthHeaders.ROLE_OWNER)
                || AuthHeaders.hasRole(role, AuthHeaders.ROLE_ADMIN) || !AuthHeaders.validUserId(userId)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
        withdrawalService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
