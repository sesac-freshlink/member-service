package com.freshlink.member.domain.store;

import com.freshlink.common.error.BusinessException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 점포 이미지 업로드 주소: 버킷 설정이 없을 때와 키 모양 검사 */
class StoreImageUploadsTest {

    @Test
    void 버킷이_설정되지_않으면_서명하지_않고_STORE_IMAGE_NOT_CONFIGURED_500() {
        S3Presigner presigner = mock(S3Presigner.class);
        StoreImageUploads uploads = new StoreImageUploads(presigner, "  ", Duration.ofMinutes(5));

        assertThatThrownBy(() -> uploads.issue(7L))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo("STORE_IMAGE_NOT_CONFIGURED");
                    assertThat(e.status()).isEqualTo(500);
                });
        verifyNoInteractions(presigner);
    }

    @Test
    void 이_점포에_발급하는_모양의_키만_받는다() {
        String uuid = UUID.randomUUID().toString();

        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/" + uuid + ".jpg")).isTrue();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/8/" + uuid + ".jpg")).isFalse();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/" + uuid + ".png")).isFalse();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/../x.jpg")).isFalse();
        assertThat(StoreImageUploads.isKeyOf(7L, null)).isFalse();
    }
}
