package com.freshlink.member.domain.board;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 게시판 응답 모양 (추가 명세 A17). 시각은 +09:00 */
public final class BoardResponses {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private BoardResponses() {
    }

    /** 공지 목록 한 줄 (본문 없음) */
    public record NoticeSummary(Long noticeId, String title, Audience audience, boolean pinned,
                                OffsetDateTime createdAt) {
        static NoticeSummary from(Notice n) {
            return new NoticeSummary(n.getId(), n.getTitle(), n.getAudience(), n.isPinned(), kst(n.getCreatedAt()));
        }
    }

    /** 공지 상세 */
    public record NoticeDetail(Long noticeId, String title, String content, Audience audience, boolean pinned,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static NoticeDetail from(Notice n) {
            return new NoticeDetail(n.getId(), n.getTitle(), n.getContent(), n.getAudience(), n.isPinned(),
                    kst(n.getCreatedAt()), kst(n.getUpdatedAt()));
        }
    }

    /** FAQ 한 줄 */
    public record FaqItem(Long faqId, String question, String answer, Audience audience, int sortOrder) {
        static FaqItem from(Faq f) {
            return new FaqItem(f.getId(), f.getQuestion(), f.getAnswer(), f.getAudience(), f.getSortOrder());
        }
    }

    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime();
    }
}
