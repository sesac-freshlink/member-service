package com.freshlink.member.domain.admin;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.domain.auth.OwnerAccounts;
import com.freshlink.member.domain.member.Member;
import com.freshlink.member.domain.member.MemberRepository;
import com.freshlink.member.domain.store.Store;
import com.freshlink.member.domain.store.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AD-01 점포·점주 등록 (명세 M6 순서).
 * 1) 점포명·이메일 중복 검사 → 2) 점포 PENDING 저장(커밋, storeId 확보) → 3) Cognito 점주 계정(트랜잭션 밖) →
 * 4·5) 점주 회원 저장 + 점포 연결·ACTIVE(새 트랜잭션).
 * 2~5단계가 실패하면 이번 요청이 만든 계정·점포를 지우고 500. 정리 실패는 대상과 함께 로그에 남긴다
 */
@Service
public class StoreRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(StoreRegistrationService.class);

    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;
    private final OwnerAccounts ownerAccounts;
    private final TransactionTemplate transactionTemplate;

    public StoreRegistrationService(StoreRepository storeRepository, MemberRepository memberRepository,
                                    OwnerAccounts ownerAccounts, TransactionTemplate transactionTemplate) {
        this.storeRepository = storeRepository;
        this.memberRepository = memberRepository;
        this.ownerAccounts = ownerAccounts;
        this.transactionTemplate = transactionTemplate;
    }

    public AdminStoreResponse register(StoreRegistrationRequest req) {
        // 1) 중복 검사 (PENDING 포함 모든 점포명, Cognito 계정은 미인증 포함)
        if (storeRepository.existsByName(req.name())) {
            throw new BusinessException(MemberErrorCode.STORE_NAME_DUPLICATED);
        }
        if (memberRepository.existsByEmail(req.ownerEmail()) || ownerAccounts.emailExists(req.ownerEmail())) {
            throw new BusinessException(MemberErrorCode.OWNER_EMAIL_DUPLICATED);
        }

        // 2) PENDING 저장 후 커밋 (동시에 같은 이름이 들어오면 UNIQUE 충돌로 409)
        Long storeId;
        try {
            storeId = transactionTemplate.execute(s -> {
                Store store = storeRepository.saveAndFlush(Store.pending(req.name(), req.address(), req.region()));
                store.assignStoreCode();
                return store.getId();
            });
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(MemberErrorCode.STORE_NAME_DUPLICATED);
        }

        // 3) Cognito 계정 (실패하면 계정은 createOwner가 정리한다)
        String sub;
        try {
            sub = ownerAccounts.createOwner(req.ownerEmail(), req.initialPassword(), storeId);
        } catch (BusinessException e) {
            // 계정 생성 중 같은 이메일 계정이 확인됨 (409). 이번 요청이 만든 점포만 지운다
            deleteStoreQuietly(storeId);
            throw e;
        } catch (RuntimeException e) {
            log.error("점포 등록 실패: Cognito 계정 생성 storeId={} ownerEmail={}", storeId, req.ownerEmail(), e);
            deleteStoreQuietly(storeId);
            throw new BusinessException(MemberErrorCode.STORE_REGISTRATION_FAILED);
        }

        // 4·5) 점주 회원 저장 + 점포 연결·ACTIVE
        try {
            return transactionTemplate.execute(s -> {
                memberRepository.saveAndFlush(Member.owner(sub, req.ownerEmail()));
                Store store = storeRepository.findByIdForUpdate(storeId).orElseThrow();
                store.activate(sub);
                storeRepository.saveAndFlush(store);
                log.info("점포 등록 완료 storeId={} storeCode={} ownerSub={}", storeId, store.getStoreCode(), sub);
                return AdminStoreResponse.from(store, req.ownerEmail());
            });
        } catch (RuntimeException e) {
            log.error("점포 등록 실패: 회원 저장·점포 연결 storeId={} ownerSub={}", storeId, sub, e);
            deleteAccountQuietly(req.ownerEmail(), storeId);
            deleteStoreQuietly(storeId);
            throw new BusinessException(MemberErrorCode.STORE_REGISTRATION_FAILED);
        }
    }

    private void deleteStoreQuietly(Long storeId) {
        try {
            transactionTemplate.executeWithoutResult(s -> storeRepository.deleteById(storeId));
        } catch (RuntimeException e) {
            log.error("점포 등록 정리 실패 (수동 정리 필요) 대상=store id={}", storeId, e);
        }
    }

    private void deleteAccountQuietly(String email, Long storeId) {
        try {
            ownerAccounts.delete(email);
        } catch (RuntimeException e) {
            log.error("점포 등록 정리 실패 (수동 정리 필요) 대상=Cognito username={} storeId={}", email, storeId, e);
        }
    }
}
