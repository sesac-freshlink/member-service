package com.freshlink.member.domain.member;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.error.UniqueKeys;
import com.freshlink.member.domain.auth.SocialAccounts;
import com.freshlink.member.domain.store.StoreRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final TransactionTemplate transactionTemplate;
    private final SocialAccounts socialAccounts;

    public MemberService(MemberRepository memberRepository, StoreRepository storeRepository,
                         TransactionTemplate transactionTemplate, SocialAccounts socialAccounts) {
        this.memberRepository = memberRepository;
        this.storeRepository = storeRepository;
        this.transactionTemplate = transactionTemplate;
        this.socialAccounts = socialAccounts;
    }

    /** MB-01: 정규화한 닉네임이 아직 없으면 사용 가능 (최종 검사는 MB-02 저장 때 UNIQUE로 한 번 더) */
    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !memberRepository.existsByNicknameNormalized(Member.normalizeNickname(nickname));
    }

    /**
     * MB-02 고객 프로필 등록. 같은 sub의 프로필이 있으면 새로 만들지 않고 그대로 돌려준다(created=false).
     * 바깥에 트랜잭션을 두지 않는다: UNIQUE 충돌이 나면 롤백이 끝난 뒤 새로 읽어 같은 sub인지 닉네임 중복인지 가린다.
     * 구글로 처음 들어온 계정인데 같은 이메일로 이메일 가입한 계정이 있으면 409 EMAIL_SIGNUP_EXISTS (C-01 BFF, 10/29 막기)
     */
    public RegisterResult register(String memberId, ProfileRequest req) {
        Optional<Member> existing = memberRepository.findById(memberId);
        if (existing.isPresent()) {
            return new RegisterResult(existing.get(), false);
        }
        if (socialAccounts.emailSignupExistsFor(memberId)) {
            throw new BusinessException(MemberErrorCode.EMAIL_SIGNUP_EXISTS);
        }
        if (memberRepository.existsByNicknameNormalized(Member.normalizeNickname(req.nickname()))) {
            // 같은 sub의 동시 요청이 방금 이 닉네임으로 저장했을 수 있다. 그러면 중복이 아니라 재요청이다
            return memberRepository.findById(memberId)
                    .map(m -> new RegisterResult(m, false))
                    .orElseThrow(() -> new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED));
        }
        try {
            Member saved = transactionTemplate.execute(s -> memberRepository.saveAndFlush(
                    Member.customer(memberId, req.name(), req.nickname(), req.region())));
            log.info("고객 프로필 등록 memberId={} region={}", memberId, req.region());
            return new RegisterResult(saved, true);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            // UNIQUE 충돌이거나 두 UNIQUE 인덱스를 동시에 잡다 생긴 교착(deadlock)이다.
            // 같은 sub가 먼저 저장했으면 그 결과를 돌려주고, 닉네임 UNIQUE 충돌이면 409, 그 밖의 무결성 오류는 그대로 던진다
            Optional<Member> saved = memberRepository.findById(memberId);
            if (saved.isPresent()) {
                return new RegisterResult(saved.get(), false);
            }
            if (e instanceof DataIntegrityViolationException d && UniqueKeys.isViolation(d, UniqueKeys.MEMBER_NICKNAME)) {
                throw new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED);
            }
            throw e;
        }
    }

    /** MB-03 내 정보. 프로필이 없으면 404 (고객 → 화면이 등록 마무리 화면으로 보낸다) */
    @Transactional(readOnly = true)
    public MeResponse me(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_PROFILE_NOT_FOUND));
        if (member.getRole() == MemberRole.OWNER) {
            return MeResponse.owner(member, storeRepository.findByOwnerMemberId(memberId).orElse(null));
        }
        return MeResponse.customer(member);
    }

    public record RegisterResult(Member member, boolean created) {
    }
}
