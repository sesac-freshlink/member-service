package com.freshlink.member.domain.board;

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

/** 공지사항 (표 notice, V5, 추가 명세 A17) */
@Entity
@Table(name = "notice")
@EntityListeners(AuditingEntityListener.class)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 10)
    private Audience audience;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Notice() {
    }

    public static Notice create(String title, String content, Audience audience, boolean pinned) {
        Notice n = new Notice();
        n.update(title, content, audience, pinned);
        return n;
    }

    /** 전체 칸을 바꾼다 (관리자 수정 화면은 모든 칸을 보낸다) */
    public void update(String title, String content, Audience audience, boolean pinned) {
        this.title = title;
        this.content = content;
        this.audience = audience;
        this.pinned = pinned;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Audience getAudience() { return audience; }
    public boolean isPinned() { return pinned; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
