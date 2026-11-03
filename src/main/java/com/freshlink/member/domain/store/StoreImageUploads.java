package com.freshlink.member.domain.store;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 점포 이미지 업로드 주소 발급 (10/30 김진아님 답변 10번·바뀐 사항).
 * 키는 서버가 images/stores/{storeId}/{uuid}.jpg(또는 .png)로 정하고, 5분짜리 presigned PUT 주소를 준다.
 * 형식은 화면이 고른 image/jpeg·image/png 중 하나이고(안 보내면 image/jpeg), 그 Content-Type을 서명에 넣는다
 * (브라우저가 다른 형식으로 올리면 S3가 거절한다). 11/2 PNG 추가(김진아님 답변: infra는 확장자를 가리지 않는다).
 * 버킷은 STORE_IMAGE_BUCKET(프론트 버킷). 화면은 같은 주소 /images/...로 읽는다
 */
@Component
public class StoreImageUploads {

    private static final Logger log = LoggerFactory.getLogger(StoreImageUploads.class);

    static final String DEFAULT_CONTENT_TYPE = "image/jpeg";
    /** 받는 형식 → 키 확장자 */
    static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", ".jpg", "image/png", ".png");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern UUID_PART =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final S3Presigner presigner;
    private final String bucket;
    private final Duration ttl;

    public StoreImageUploads(S3Presigner presigner, @Value("${app.store-image.bucket:}") String bucket,
                             @Value("${app.store-image.upload-ttl:5m}") Duration ttl) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.ttl = ttl;
    }

    /** 그 점포의 새 이미지 키와 업로드 주소. contentType이 없으면 image/jpeg, JPG·PNG가 아니면 400 */
    public UploadUrl issue(Long storeId, String contentType) {
        String type = contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType;
        String extension = EXTENSIONS.get(type);
        if (extension == null) {
            throw new BusinessException(MemberErrorCode.STORE_IMAGE_TYPE_INVALID);
        }
        if (bucket == null || bucket.isBlank()) {
            // 부르기 전에 알 수 있는 설정 오류다 (STORE_IMAGE_BUCKET은 11/2 클러스터부터 들어간다). 개발자가 볼 수 있게 남긴다
            log.error("STORE_IMAGE_BUCKET이 설정되지 않아 이미지 업로드 주소를 만들지 않는다 storeId={}", storeId);
            throw new BusinessException(MemberErrorCode.STORE_IMAGE_NOT_CONFIGURED);
        }
        String key = keyPrefix(storeId) + UUID.randomUUID() + extension;
        PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl).putObjectRequest(put).build());
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(ttl).atZoneSameInstant(KST).toOffsetDateTime();
        return new UploadUrl(signed.url().toString(), key, "/" + key, type, expiresAt);
    }

    /** 이 점포에 발급하는 모양의 키인지 (images/stores/{storeId}/{uuid}.jpg 또는 .png) */
    public static boolean isKeyOf(Long storeId, String key) {
        String prefix = keyPrefix(storeId);
        if (key == null || !key.startsWith(prefix)) {
            return false;
        }
        for (String extension : EXTENSIONS.values()) {
            if (key.endsWith(extension)) {
                return UUID_PART.matcher(key.substring(prefix.length(), key.length() - extension.length())).matches();
            }
        }
        return false;
    }

    private static String keyPrefix(Long storeId) {
        return "images/stores/" + storeId + "/";
    }

    /** uploadUrl로 PUT(Content-Type은 contentType 그대로)한 뒤 imageKey를 점포 수정에 넣는다. imageUrl은 화면이 읽는 주소 */
    public record UploadUrl(String uploadUrl, String imageKey, String imageUrl, String contentType,
                            OffsetDateTime expiresAt) {
    }
}
