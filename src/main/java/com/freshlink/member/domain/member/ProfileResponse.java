package com.freshlink.member.domain.member;

import com.freshlink.member.domain.store.Region;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** MB-02 응답. createdAt은 +09:00 */
public record ProfileResponse(String memberId, MemberRole role, String name, String nickname, Region region,
                              OffsetDateTime createdAt) {

    public static ProfileResponse from(Member m) {
        OffsetDateTime createdAt = m.getCreatedAt() == null ? null
                : m.getCreatedAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        return new ProfileResponse(m.getId(), m.getRole(), m.getName(), m.getNickname(), m.getRegion(), createdAt);
    }
}
