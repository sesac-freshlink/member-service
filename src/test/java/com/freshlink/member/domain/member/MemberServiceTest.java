package com.freshlink.member.domain.member;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.domain.auth.SocialAccounts;
import com.freshlink.member.domain.store.Region;
import com.freshlink.member.domain.store.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MB-02 동시 요청의 끼어드는 순서를 목으로 고정해 확인한다 (통합 테스트는 순서가 매번 달라 재현이 안 될 수 있다) */
class MemberServiceTest {

    private static final String SUB = "11111111-1111-1111-1111-111111111111";

    MemberRepository members = mock(MemberRepository.class);
    SocialAccounts socialAccounts = mock(SocialAccounts.class);
    MemberService service = new MemberService(members, mock(StoreRepository.class),
            new TransactionTemplate(mock(PlatformTransactionManager.class)), socialAccounts);
    ProfileRequest req = new ProfileRequest("김경우", "Fresh01", Region.SEOUL);
    Member existing = Member.customer(SUB, "김경우", "Fresh01", Region.SEOUL);

    @Test
    void 처음_조회_뒤_같은_sub가_먼저_저장해_닉네임이_쓰였으면_409가_아니라_기존_프로필() {
        when(members.findById(SUB)).thenReturn(Optional.empty(), Optional.of(existing));
        when(members.existsByNicknameNormalized("fresh01")).thenReturn(true);

        MemberService.RegisterResult r = service.register(SUB, req);

        assertThat(r.created()).isFalse();
        assertThat(r.member()).isSameAs(existing);
    }

    @Test
    void 다른_sub가_닉네임을_쓰고_있으면_409() {
        when(members.findById(SUB)).thenReturn(Optional.empty());
        when(members.existsByNicknameNormalized("fresh01")).thenReturn(true);

        assertThatThrownBy(() -> service.register(SUB, req)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("NICKNAME_DUPLICATED"));
    }

    @Test
    void 저장_중_교착이_나도_같은_sub가_저장돼_있으면_기존_프로필() {
        when(members.findById(SUB)).thenReturn(Optional.empty(), Optional.of(existing));
        when(members.saveAndFlush(any(Member.class))).thenThrow(new CannotAcquireLockException("Deadlock found"));

        MemberService.RegisterResult r = service.register(SUB, req);

        assertThat(r.created()).isFalse();
    }

    @Test
    void 저장_중_UNIQUE_충돌이고_같은_sub가_없으면_409() {
        when(members.findById(SUB)).thenReturn(Optional.empty());
        when(members.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry 'fresh01' for key 'member.uk_member_nickname_normalized'"));

        assertThatThrownBy(() -> service.register(SUB, req)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("NICKNAME_DUPLICATED"));
    }

    @Test
    void 닉네임_충돌이_아닌_무결성_오류는_409로_바꾸지_않고_그대로_던진다() {
        DataIntegrityViolationException tooLong = new DataIntegrityViolationException("Data too long for column 'name'");
        when(members.findById(SUB)).thenReturn(Optional.empty());
        when(members.saveAndFlush(any(Member.class))).thenThrow(tooLong);

        assertThatThrownBy(() -> service.register(SUB, req)).isSameAs(tooLong);
    }

    @Test
    void 교착인데_같은_sub도_없으면_원래_예외를_던진다() {
        CannotAcquireLockException deadlock = new CannotAcquireLockException("Deadlock found");
        when(members.findById(SUB)).thenReturn(Optional.empty());
        when(members.saveAndFlush(any(Member.class))).thenThrow(deadlock);

        assertThatThrownBy(() -> service.register(SUB, req)).isSameAs(deadlock);
    }

    @Test
    void 구글로_처음_들어온_계정인데_같은_이메일_가입_계정이_있으면_409_저장하지_않는다() {
        when(members.findById(SUB)).thenReturn(Optional.empty());
        when(socialAccounts.emailSignupExistsFor(SUB)).thenReturn(true);

        assertThatThrownBy(() -> service.register(SUB, req)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("EMAIL_SIGNUP_EXISTS"));
        verify(members, never()).saveAndFlush(any(Member.class));
    }

    @Test
    void 이미_프로필이_있으면_구글_이메일_확인을_하지_않는다() {
        when(members.findById(SUB)).thenReturn(Optional.of(existing));

        assertThat(service.register(SUB, req).created()).isFalse();
        verify(socialAccounts, never()).emailSignupExistsFor(SUB);
    }
}
