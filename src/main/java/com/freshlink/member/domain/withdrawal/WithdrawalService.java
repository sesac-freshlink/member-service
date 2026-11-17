package com.freshlink.member.domain.withdrawal;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.domain.member.Member;
import com.freshlink.member.domain.member.MemberRepository;
import com.freshlink.member.domain.member.MemberRole;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * 고객 회원탈퇴 (추가 명세 A6·A7). 순서:
 * <ol>
 *   <li>프로필이 없으면 404, DB 역할이 고객이 아니면 403</li>
 *   <li>order-service에 진행 중 주문을 묻는다. 있으면 409, 묻지 못하거나 수가 음수면 503(탈퇴하지 않는다)</li>
 *   <li>Cognito 계정을 지운다(다시 로그인할 수 없게 먼저). 실패하면 502, 아무것도 바뀌지 않는다</li>
 *   <li>한 트랜잭션에서 탈퇴 이력(이메일·역할·시각)을 남기고 프로필을 지운다. 거주 지역은 보관하지 않는다</li>
 * </ol>
 * 4가 실패하면 계정은 지워졌고 프로필만 남는다. 다시 로그인할 수 없어 화면에는 탈퇴로 보이며, 오류 로그로 남겨 손으로 정리한다.
 * 프로필을 지우면 닉네임·이메일로 다시 가입할 수 있고 새 회원 ID로 시작한다. 주문은 고객 이름을 저장하지 않아 바꿀 것이 없다
 */
@Service
public class WithdrawalService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final MemberRepository memberRepository;
    private final MemberWithdrawalRepository withdrawalRepository;
    private final OrderActivity orderActivity;
    private final CustomerAccounts customerAccounts;
    private final TransactionTemplate transactionTemplate;
    private final Duration retention;

    public WithdrawalService(MemberRepository memberRepository, MemberWithdrawalRepository withdrawalRepository,
                             OrderActivity orderActivity, CustomerAccounts customerAccounts,
                             TransactionTemplate transactionTemplate,
                             @Value("${app.withdrawal.retention:1825d}") Duration retention) {
        this.memberRepository = memberRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.orderActivity = orderActivity;
        this.customerAccounts = customerAccounts;
        this.transactionTemplate = transactionTemplate;
        this.retention = retention;
    }

    public void withdraw(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_PROFILE_NOT_FOUND));
        if (member.getRole() != MemberRole.CUSTOMER) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
        long active;
        try {
            active = orderActivity.activeOrders(memberId);
        } catch (RuntimeException e) {
            log.warn("탈퇴 전 진행 중 주문 확인 실패 memberId={} error={}", memberId, e.getClass().getSimpleName());
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE);
        }
        if (active < 0) {
            // 진행 중 주문이 없다는 확인이 아니다. 묻지 못한 것과 같이 다룬다
            log.warn("탈퇴 전 진행 중 주문 수가 음수 memberId={} activeOrders={}", memberId, active);
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_CHECK_UNAVAILABLE);
        }
        if (active > 0) {
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_BLOCKED_ACTIVE_ORDER);
        }
        Optional<String> email;
        try {
            email = customerAccounts.delete(memberId);
        } catch (SdkException e) {
            log.warn("탈퇴 Cognito 계정 삭제 실패 memberId={} error={}", memberId, e.getClass().getSimpleName());
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_FAILED);
        }
        OffsetDateTime now = OffsetDateTime.now(KST);
        try {
            transactionTemplate.executeWithoutResult(s -> {
                withdrawalRepository.save(MemberWithdrawal.self(memberId, email.orElse(null), member.getRole(), now));
                memberRepository.delete(member);
            });
        } catch (RuntimeException e) {
            log.error("탈퇴: Cognito 계정은 지웠지만 프로필 정리 실패, 손으로 정리 필요 memberId={}", memberId, e);
            throw e;
        }
        log.info("회원탈퇴 memberId={} role={}", memberId, member.getRole());
    }

    /** 보관 기간(5년, 11/14 경우 결정)이 지난 탈퇴 이력을 하루 한 번 지운다. 여러 파드가 같이 돌아도 결과는 같다 */
    @Scheduled(cron = "${app.withdrawal.cleanup-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpiredHistory() {
        int deleted = transactionTemplate.execute(s ->
                withdrawalRepository.deleteOlderThan(OffsetDateTime.now(KST).minus(retention)));
        if (deleted > 0) {
            log.info("보관 기간이 지난 탈퇴 이력 삭제 count={}", deleted);
        }
    }
}
