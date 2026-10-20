package com.freshlink.member.domain.admin;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.error.UniqueKeys;
import com.freshlink.member.domain.member.MemberRepository;
import com.freshlink.member.domain.store.Store;
import com.freshlink.member.domain.store.StoreRepository;
import com.freshlink.member.domain.store.StoreStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminStoreService {

    private static final Logger log = LoggerFactory.getLogger(AdminStoreService.class);
    static final int PAGE_SIZE = 20;

    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    public AdminStoreService(StoreRepository storeRepository, MemberRepository memberRepository,
                             TransactionTemplate transactionTemplate) {
        this.storeRepository = storeRepository;
        this.memberRepository = memberRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** AD-02: 모든 상태, 등록일 최신순, 20개씩 */
    @Transactional(readOnly = true)
    public PageResponse<AdminStoreResponse> list(int page) {
        return PageResponse.of(storeRepository.findAllWithOwner(PageRequest.of(page, PAGE_SIZE))
                .map(row -> AdminStoreResponse.from(row.store(), row.ownerEmail())));
    }

    /**
     * AD-03: 등록 완료(ACTIVE·INACTIVE) 점포의 이름·주소·시도만 바꾼다. 행을 잠근 채 읽고 고쳐 동시 수정이 서로 덮어쓰지 않는다.
     * 이름 중복은 미리 보고, 동시에 같은 이름으로 바꾸는 경우는 UNIQUE(uk_store_name) 충돌로 막는다
     */
    public AdminStoreResponse update(Long storeId, AdminStoreUpdateRequest req) {
        try {
            return transactionTemplate.execute(s -> {
                Store store = storeRepository.findByIdForUpdate(storeId)
                        .orElseThrow(() -> new BusinessException(MemberErrorCode.STORE_NOT_FOUND));
                if (store.getStatus() == StoreStatus.PENDING) {
                    throw new BusinessException(MemberErrorCode.STORE_NOT_EDITABLE);
                }
                if (req.name() != null && storeRepository.existsByNameAndIdNot(req.name(), storeId)) {
                    throw new BusinessException(MemberErrorCode.STORE_NAME_DUPLICATED);
                }
                store.update(req.name(), req.address(), req.region());
                storeRepository.saveAndFlush(store);
                log.info("점포 수정 storeId={} nameChanged={} addressChanged={} region={}",
                        storeId, req.name() != null, req.address() != null, req.region());
                return AdminStoreResponse.from(store, ownerEmail(store));
            });
        } catch (DataIntegrityViolationException e) {
            if (UniqueKeys.isViolation(e, UniqueKeys.STORE_NAME)) {
                throw new BusinessException(MemberErrorCode.STORE_NAME_DUPLICATED);
            }
            throw e;
        }
    }

    private String ownerEmail(Store store) {
        return store.getOwnerMemberId() == null ? null
                : memberRepository.findById(store.getOwnerMemberId()).map(m -> m.getEmail()).orElse(null);
    }
}
