package com.freshlink.member.domain.owner;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.domain.store.Store;
import com.freshlink.member.domain.store.StoreImageUploads;
import com.freshlink.member.domain.store.StoreRepository;
import com.freshlink.member.domain.store.StoreStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 점주 내 점포 (추가 명세 A15). 점주는 자기 점포의 설명·이미지만 고친다. 이름·주소·시도·좌표는 관리자(AD-03)만 */
@Service
public class OwnerStoreService {

    private static final Logger log = LoggerFactory.getLogger(OwnerStoreService.class);

    private final StoreRepository storeRepository;
    private final StoreImageUploads storeImageUploads;

    public OwnerStoreService(StoreRepository storeRepository, StoreImageUploads storeImageUploads) {
        this.storeRepository = storeRepository;
        this.storeImageUploads = storeImageUploads;
    }

    /** OW-01: 연결된 점포가 없으면 404 */
    @Transactional(readOnly = true)
    public OwnerStoreResponse get(String ownerId) {
        return OwnerStoreResponse.from(storeRepository.findByOwnerMemberId(ownerId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.STORE_NOT_FOUND)));
    }

    /**
     * OW-02: 보낸 값만 바꾼다. 설명은 빈 값("")이면 지우고, 이미지는 바꾸기만 한다(지우기 없음).
     * 관리자 수정(AD-03)과 같이 행을 잠근 채 읽고 고쳐, 관리자와 점주가 동시에 고쳐도 서로의 칸을 덮어쓰지 않는다
     */
    @Transactional
    public OwnerStoreResponse update(String ownerId, OwnerStoreUpdateRequest req) {
        Store store = storeRepository.findByOwnerMemberIdForUpdate(ownerId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.STORE_NOT_FOUND));
        if (store.getStatus() == StoreStatus.PENDING) {
            throw new BusinessException(MemberErrorCode.STORE_NOT_EDITABLE);
        }
        if (req.imageKey() != null && !StoreImageUploads.isKeyOf(store.getId(), req.imageKey())) {
            throw new BusinessException(MemberErrorCode.STORE_IMAGE_KEY_INVALID);
        }
        store.updateDetail(null, null, req.description(), req.imageKey());
        storeRepository.saveAndFlush(store);
        log.info("점주 점포 수정 storeId={} descriptionChanged={} imageChanged={}",
                store.getId(), req.description() != null, req.imageKey() != null);
        return OwnerStoreResponse.from(store);
    }

    /** OW-03: 연결된 점포가 없으면 404, 등록 중이면 409, JPG·PNG가 아니면 400 (관리자 발급과 같은 키·주소) */
    @Transactional(readOnly = true)
    public StoreImageUploads.UploadUrl imageUploadUrl(String ownerId, String contentType) {
        Store store = storeRepository.findByOwnerMemberId(ownerId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.STORE_NOT_FOUND));
        if (store.getStatus() == StoreStatus.PENDING) {
            throw new BusinessException(MemberErrorCode.STORE_NOT_EDITABLE);
        }
        StoreImageUploads.UploadUrl url = storeImageUploads.issue(store.getId(), contentType);
        log.info("점주 점포 이미지 업로드 주소 발급 storeId={} key={} contentType={}", store.getId(), url.imageKey(),
                url.contentType());
        return url;
    }
}
