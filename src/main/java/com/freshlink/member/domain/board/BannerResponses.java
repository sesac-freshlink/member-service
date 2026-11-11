package com.freshlink.member.domain.board;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 배너 응답 모양 (추가 명세 A18). 시각은 +09:00 */
public final class BannerResponses {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private BannerResponses() {
    }

    /** BN-01 홈 배너 한 장 (frontend HomeBanner 와 같은 모양) */
    public record HomeBanner(Long bannerId, String title, String imageUrl, Long noticeId) {
        /** 배너를 홈 응답 한 장으로 바꾼다 */
        static HomeBanner from(Banner b) {
            return new HomeBanner(b.getId(), b.getTitle(), b.getImageUrl(), b.getNoticeId());
        }
    }

    /** AD-13~AD-17 관리자 목록 한 줄. noticeTitle 은 화면 표시용(공지가 없으면 null) */
    public record AdminBanner(Long bannerId, String title, String imageUrl, Long noticeId, String noticeTitle,
                              int sortOrder, boolean visible, OffsetDateTime startsAt, OffsetDateTime endsAt,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        /** 배너를 관리자 목록 한 줄로 바꾼다. 시각은 +09:00 */
        static AdminBanner from(Banner b, String noticeTitle) {
            return new AdminBanner(b.getId(), b.getTitle(), b.getImageUrl(), b.getNoticeId(), noticeTitle,
                    b.getSortOrder(), b.isVisible(), kst(b.getStartsAt()), kst(b.getEndsAt()),
                    kst(b.getCreatedAt()), kst(b.getUpdatedAt()));
        }
    }

    /** 시각을 서울 시간(+09:00)으로 바꾼다. 비었으면 null */
    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime();
    }
}
