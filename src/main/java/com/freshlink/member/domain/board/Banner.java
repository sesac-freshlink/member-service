package com.freshlink.member.domain.board;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 홈 메인 배너 (표 banner, V6, 추가 명세 A18). 연결한 공지는 ID만 들고 있다.
 * 공지를 지우면 DB 외래 키(ON DELETE SET NULL)가 notice_id 를 비운다
 */
@Entity
@Table(name = "banner")
@EntityListeners(AuditingEntityListener.class)
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(name = "notice_id")
    private Long noticeId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Banner() {
    }

    public static Banner create(BannerRequest req) {
        Banner b = new Banner();
        b.update(req);
        return b;
    }

    /** 전체 칸을 바꾼다 (관리자 수정 화면은 모든 칸을 보낸다) */
    public void update(BannerRequest req) {
        this.title = req.title();
        this.imageUrl = req.imageUrl();
        this.noticeId = req.noticeId();
        this.sortOrder = req.sortOrderOrZero();
        this.visible = req.visibleOrTrue();
        this.startsAt = req.startsAt();
        this.endsAt = req.endsAt();
    }

    /** 순서만 바꾼다. 순서 바꾸기(AD-17)에서만 쓴다 */
    void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageUrl() { return imageUrl; }
    public Long getNoticeId() { return noticeId; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
