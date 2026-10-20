package com.freshlink.member.domain.member;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /** MB-01 닉네임 중복 확인 (공개). 형식이 틀리면 400 */
    @GetMapping("/nickname-availability")
    public NicknameAvailabilityResponse nicknameAvailability(
            @RequestParam(required = false) @NotNull @Pattern(regexp = "^\\s*[가-힣a-zA-Z0-9]{2,10}\\s*$") String nickname) {
        return new NicknameAvailabilityResponse(nickname, memberService.isNicknameAvailable(nickname));
    }

    /** MB-02 고객 프로필 등록: 새로 저장 201, 이미 있으면 200 */
    @PostMapping("/me")
    public ResponseEntity<ProfileResponse> register(
            @RequestHeader(name = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @Valid @RequestBody ProfileRequest request) {
        if (!AuthHeaders.hasRole(role, AuthHeaders.ROLE_CUSTOMER) || !AuthHeaders.validUserId(userId)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
        MemberService.RegisterResult result = memberService.register(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ProfileResponse.from(result.member()));
    }

    /** MB-03 내 정보 (CUSTOMER·OWNER) */
    @GetMapping("/me")
    public MeResponse me(
            @RequestHeader(name = AuthHeaders.USER_ID, required = false) String userId,
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        boolean allowed = AuthHeaders.hasRole(role, AuthHeaders.ROLE_CUSTOMER)
                || AuthHeaders.hasRole(role, AuthHeaders.ROLE_OWNER);
        if (!allowed || !AuthHeaders.validUserId(userId)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
        return memberService.me(userId);
    }
}
