package com.freshlink.member.domain.store;

import com.freshlink.common.error.BusinessException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/** 점포 이미지 업로드 주소: 서명에 넣는 형식, 버킷 설정이 없을 때, 받지 않는 형식, 키 모양 검사 */
class StoreImageUploadsTest {

    @Test
    void PNG는_png_키에_image_png로_형식이_없으면_jpg_키에_image_jpeg로_서명한다() throws Exception {
        String[][] cases = {{"image/png", "image/png", ".png"}, {null, "image/jpeg", ".jpg"}};
        for (String[] c : cases) {
            S3Presigner presigner = mock(S3Presigner.class);
            PresignedPutObjectRequest signed = mock(PresignedPutObjectRequest.class);
            when(signed.url()).thenReturn(URI.create("https://bucket.s3.ap-northeast-2.amazonaws.com/x").toURL());
            when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
            StoreImageUploads uploads = new StoreImageUploads(presigner, "bucket", Duration.ofMinutes(5));

            StoreImageUploads.UploadUrl url = uploads.issue(7L, c[0]);

            ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
            verify(presigner).presignPutObject(captor.capture());
            assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo(c[1]);
            assertThat(captor.getValue().putObjectRequest().key()).startsWith("images/stores/7/").endsWith(c[2])
                    .isEqualTo(url.imageKey());
            assertThat(url.contentType()).isEqualTo(c[1]);
        }
    }

    @Test
    void 버킷이_설정되지_않으면_서명하지_않고_STORE_IMAGE_NOT_CONFIGURED_500() {
        S3Presigner presigner = mock(S3Presigner.class);
        StoreImageUploads uploads = new StoreImageUploads(presigner, "  ", Duration.ofMinutes(5));

        assertThatThrownBy(() -> uploads.issue(7L, "image/jpeg"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo("STORE_IMAGE_NOT_CONFIGURED");
                    assertThat(e.status()).isEqualTo(500);
                });
        verifyNoInteractions(presigner);
    }

    @Test
    void JPG_PNG가_아닌_형식은_서명하지_않고_STORE_IMAGE_TYPE_INVALID_400() {
        S3Presigner presigner = mock(S3Presigner.class);
        StoreImageUploads uploads = new StoreImageUploads(presigner, "bucket", Duration.ofMinutes(5));

        for (String type : new String[]{"image/gif", "image/webp", "text/plain"}) {
            assertThatThrownBy(() -> uploads.issue(7L, type))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.code()).isEqualTo("STORE_IMAGE_TYPE_INVALID");
                        assertThat(e.status()).isEqualTo(400);
                    });
        }
        verifyNoInteractions(presigner);
    }

    @Test
    void 이_점포에_발급하는_모양의_키만_받는다() {
        String uuid = UUID.randomUUID().toString();

        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/" + uuid + ".jpg")).isTrue();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/" + uuid + ".png")).isTrue();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/8/" + uuid + ".jpg")).isFalse();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/" + uuid + ".gif")).isFalse();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/" + uuid + ".jpg.png")).isFalse();
        assertThat(StoreImageUploads.isKeyOf(7L, "images/stores/7/../x.jpg")).isFalse();
        assertThat(StoreImageUploads.isKeyOf(7L, null)).isFalse();
    }
}
