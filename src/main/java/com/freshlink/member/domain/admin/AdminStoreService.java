package com.freshlink.member.domain.admin;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.error.UniqueKeys;
import com.freshlink.member.domain.member.MemberRepository;
import com.freshlink.member.domain.store.Store;
import com.freshlink.member.domain.store.StoreImageUploads;
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
    private final StoreImageUploads storeImageUploads;

    public AdminStoreService(StoreRepository storeRepository, MemberRepository memberRepository,
                             TransactionTemplate transactionTemplate, StoreImageUploads storeImageUploads) {
        this.storeRepository = storeRepository;
        this.memberRepository = memberRepository;
        this.transactionTemplate = transactionTemplate;
        this.storeImageUploads = storeImageUploads;
    }

    /** AD-02: 모든 상태, 등록일 최신순, 20개씩 */
    @Transactional(readOnly = true)
    public PageResponse<AdminStoreResponse> list(int page) {
        return PageResponse.of(storeRepository.findAllWithOwner(PageRequest.of(page, PAGE_SIZE))
                .map(row -> AdminStoreResponse.from(row.store(), row.ownerEmail())));
    }

    /**
     * AD-03: 등록 완료(ACTIVE·INACTIVE) 점포의 이름·주소·시도와 점포 상세 값(좌표·설명·이미지 키)을 바꾼다.
     * 이미지 키는 이 점포에 발급하는 모양(images/stores/{storeId}/{uuid}.jpg)만 받는다. 행을 잠근 채 읽고 고쳐 동시 수정이 서로 덮어쓰지 않는다.
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
                if (req.imageKey() != null && !StoreImageUploads.isKeyOf(storeId, req.imageKey())) {
                    throw new BusinessException(MemberErrorCode.STORE_IMAGE_KEY_INVALID);
                }
                store.update(req.name(), req.address(), req.region());
                store.updateDetail(req.latitude(), req.longitude(), req.description(), req.imageKey());
                storeRepository.saveAndFlush(store);
                log.info("점포 수정 storeId={} nameChanged={} addressChanged={} region={} detailChanged={} imageChanged={}",
                        storeId, req.name() != null, req.address() != null, req.region(),
                        req.latitude() != null || req.longitude() != null || req.description() != null,
                        req.imageKey() != null);
                return AdminStoreResponse.from(store, ownerEmail(store));
            });
        } catch (DataIntegrityViolationException e) {
            if (UniqueKeys.isViolation(e, UniqueKeys.STORE_NAME)) {
                throw new BusinessException(MemberErrorCode.STORE_NAME_DUPLICATED);
            }
            throw e;
        }
    }

    /** 점포 이미지 업로드 주소 발급: 없으면 404, 등록 중이면 409 (점포 수정과 같은 조건) */
    @Transactional(readOnly = true)
    public StoreImageUploads.UploadUrl imageUploadUrl(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.STORE_NOT_FOUND));
        if (store.getStatus() == StoreStatus.PENDING) {
            throw new BusinessException(MemberErrorCode.STORE_NOT_EDITABLE);
        }
        StoreImageUploads.UploadUrl url = storeImageUploads.issue(storeId);
        log.info("점포 이미지 업로드 주소 발급 storeId={} key={}", storeId, url.imageKey());
        return url;
    }

    private String ownerEmail(Store store) {
        return store.getOwnerMemberId() == null ? null
                : memberRepository.findById(store.getOwnerMemberId()).map(m -> m.getEmail()).orElse(null);
    }
}
