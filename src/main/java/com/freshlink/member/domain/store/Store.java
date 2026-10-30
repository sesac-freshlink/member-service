package com.freshlink.member.domain.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 점포 (표 store, V1). 점포 ID = Cognito custom:storeId */
@Entity
@Table(name = "store")
@EntityListeners(AuditingEntityListener.class)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_code", length = 20, unique = true)
    private String storeCode;

    @Column(name = "name", nullable = false, length = 30, unique = true)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 20)
    private Region region;

    /** 점포 상세 지도 좌표 (V2). 비어 있어도 된다 */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /** 점포 상세 설명 (V2) */
    @Column(name = "description", length = 500)
    private String description;

    /** 점포 이미지 S3 키 images/stores/{storeId}/{uuid}.jpg (V2) */
    @Column(name = "image_key", length = 255)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoreStatus status;

    @Column(name = "owner_member_id", length = 36, unique = true)
    private String ownerMemberId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Store() {
    }

    /** AD-01 1단계: 등록 중(PENDING) 점포. 점주는 아직 없다 */
    public static Store pending(String name, String address, Region region) {
        Store s = new Store();
        s.name = name;
        s.address = address;
        s.region = region;
        s.status = StoreStatus.PENDING;
        return s;
    }

    /** 점포 코드 S + ID (0 채움 없음). INSERT 직후 같은 트랜잭션에서 한 번만 정한다 */
    public void assignStoreCode() {
        if (this.storeCode != null) {
            throw new IllegalStateException("점포 코드가 이미 있다: " + storeCode);
        }
        this.storeCode = "S" + id;
    }

    /** AD-01 마지막 단계: 점주를 연결하고 ACTIVE로 */
    public void activate(String ownerMemberId) {
        if (status != StoreStatus.PENDING) {
            throw new IllegalStateException("등록 중인 점포만 활성화한다: storeId=" + id + " status=" + status);
        }
        this.ownerMemberId = ownerMemberId;
        this.status = StoreStatus.ACTIVE;
    }

    /** AD-03: 보낸 값만 바꾼다. 점포 ID·코드·점주 연결·상태는 그대로다. 등록 중(PENDING)은 부르는 쪽이 막는다 */
    public void update(String name, String address, Region region) {
        if (name != null) {
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
        if (region != null) {
            this.region = region;
        }
    }

    /** AD-03 점포 상세 값: 보낸 값만 바꾼다 (좌표·설명·이미지 키) */
    public void updateDetail(BigDecimal latitude, BigDecimal longitude, String description, String imageKey) {
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
        if (description != null) {
            this.description = description;
        }
        if (imageKey != null) {
            this.imageKey = imageKey;
        }
    }

    /** 화면이 읽는 이미지 주소. 이미지는 프론트와 같은 주소(CloudFront)의 /images/...에 있다. 없으면 null */
    public String imageUrl() {
        return imageKey == null ? null : "/" + imageKey;
    }

    public Long getId() { return id; }
    public String getStoreCode() { return storeCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Region getRegion() { return region; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getDescription() { return description; }
    public String getImageKey() { return imageKey; }
    public StoreStatus getStatus() { return status; }
    public String getOwnerMemberId() { return ownerMemberId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
