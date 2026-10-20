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

    public Long getId() { return id; }
    public String getStoreCode() { return storeCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Region getRegion() { return region; }
    public StoreStatus getStatus() { return status; }
    public String getOwnerMemberId() { return ownerMemberId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
