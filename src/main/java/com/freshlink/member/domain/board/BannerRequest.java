package com.freshlink.member.domain.board;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 관리자 배너 등록·고치기 (추가 명세 A18, AD-14·AD-15). 모든 칸을 보낸다.
 * 이미지는 지금 frontend 가 가진 그림 경로(/banners/…)만 받는다. S3 연결 뒤 /images/banners/… 도 받는다.
 * 경로는 같은 사이트 안이어야 해서 슬래시로 시작하고, 파일 이름에 다른 폴더(/)나 주소(:)를 넣을 수 없다
 */
public record BannerRequest(
        @NotBlank(message = "필수입니다") @Size(max = 50, message = "50자 이하여야 합니다") String title,
        @NotBlank(message = "필수입니다") @Size(max = 255, message = "255자 이하여야 합니다")
        @Pattern(regexp = IMAGE_PATH, message = "배너 그림 경로가 아닙니다") String imageUrl,
        @Positive(message = "1 이상이어야 합니다") Long noticeId,
        @Min(value = 0, message = "0 이상이어야 합니다") @Max(value = 9999, message = "9999 이하여야 합니다") Integer sortOrder,
        Boolean visible,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
) {

    /** /banners/파일이름.jpg|jpeg|png|webp|svg (S3 연결 뒤에는 /images/banners/ 도) */
    static final String IMAGE_PATH = "^/(images/)?banners/[A-Za-z0-9][A-Za-z0-9._-]*\\.(jpg|jpeg|png|webp|svg)$";

    /** 받은 제목·그림 경로의 앞뒤 공백을 지운다 (게시판 글과 같은 규칙) */
    public BannerRequest {
        title = BoardText.trim(title);
        imageUrl = BoardText.trim(imageUrl);
    }

    /** 순서를 비웠으면 0 */
    public int sortOrderOrZero() {
        return sortOrder == null ? 0 : sortOrder;
    }

    /** 비우면 보이기 켬 */
    public boolean visibleOrTrue() {
        return !Boolean.FALSE.equals(visible);
    }

    /** 시작·끝이 둘 다 있으면 시작이 끝보다 빨라야 한다 */
    public boolean periodValid() {
        return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
    }
}
