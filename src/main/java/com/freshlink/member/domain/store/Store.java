package com.freshlink.member.domain.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 점포 (표 store, V1). 점포 ID = Cognito custom:storeId. 등록(AD-01)은 다음 작업에서 붙인다 */
@Entity
@Table(name = "store")
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Store() {
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
