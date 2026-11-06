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
    ProfileRequest req = new ProfileRequest("김경우", "Fresh01", Region.SEOUL, true, true);
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

    // ---------------------------------------------------------------- MB-04

    @Test
    void 수정_닉네임_중복_검사는_내_sub를_빼고_본다() {
        when(members.findById(SUB)).thenReturn(Optional.of(existing));
        when(members.existsByNicknameNormalizedAndIdNot("fresh01", SUB)).thenReturn(false);

        Member m = service.updateProfile(SUB, new ProfileUpdateRequest(null, "FRESH01", null));

        assertThat(m.getNickname()).isEqualTo("FRESH01");
        assertThat(m.getNicknameNormalized()).isEqualTo("fresh01");
        assertThat(m.getName()).isEqualTo("김경우");
        assertThat(m.getRegion()).isEqualTo(Region.SEOUL);
        verify(members, never()).existsByNicknameNormalized(any());
    }

    @Test
    void 수정_닉네임을_다른_회원이_쓰면_409이고_아무것도_안_바뀐다() {
        when(members.findById(SUB)).thenReturn(Optional.of(existing));
        when(members.existsByNicknameNormalizedAndIdNot("taken1", SUB)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(SUB, new ProfileUpdateRequest("이태우", "Taken1", Region.BUSAN)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("NICKNAME_DUPLICATED"));
        assertThat(existing.getName()).isEqualTo("김경우");
        assertThat(existing.getRegion()).isEqualTo(Region.SEOUL);
        assertThat(existing.getNickname()).isEqualTo("Fresh01");
        assertThat(existing.getNicknameNormalized()).isEqualTo("fresh01");
    }

    @Test
    void 닉네임을_안_보내면_중복_검사를_하지_않는다() {
        when(members.findById(SUB)).thenReturn(Optional.of(existing));

        service.updateProfile(SUB, new ProfileUpdateRequest(null, null, Region.JEJU));

        assertThat(existing.getRegion()).isEqualTo(Region.JEJU);
        verify(members, never()).existsByNicknameNormalizedAndIdNot(any(), any());
    }

    @Test
    void 점주_회원은_수정할_수_없다_403() {
        when(members.findById(SUB)).thenReturn(Optional.of(Member.owner(SUB, "owner@test.com")));

        assertThatThrownBy(() -> service.updateProfile(SUB, new ProfileUpdateRequest(null, null, Region.JEJU)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void 수정_저장_중_닉네임_UNIQUE_충돌이면_409() {
        when(members.findById(SUB)).thenReturn(Optional.of(existing));
        when(members.saveAndFlush(any(Member.class))).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry 'taken1' for key 'member.uk_member_nickname_normalized'"));

        assertThatThrownBy(() -> service.updateProfile(SUB, new ProfileUpdateRequest(null, "Taken1", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("NICKNAME_DUPLICATED"));
    }

    @Test
    void 수정_저장_중_닉네임_충돌이_아닌_무결성_오류는_그대로_던진다() {
        DataIntegrityViolationException tooLong = new DataIntegrityViolationException("Data too long for column 'name'");
        when(members.findById(SUB)).thenReturn(Optional.of(existing));
        when(members.saveAndFlush(any(Member.class))).thenThrow(tooLong);

        assertThatThrownBy(() -> service.updateProfile(SUB, new ProfileUpdateRequest("이태우", null, null))).isSameAs(tooLong);
    }
}
