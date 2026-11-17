package com.freshlink.member.domain.board;

import com.freshlink.member.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AD-18 관리자 배너 그림 업로드 주소 (추가 명세 A18, 11/16).
 * 주소 서명은 테스트용 고정 자격으로 하고 S3에는 접속하지 않는다
 */
@SpringBootTest(properties = "app.store-image.bucket=freshlink-test-frontend")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BannerImageUploadIntegrationTest {

    private static final String UUID_PART = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    MockMvc mockMvc;
    @TestBean
    S3Presigner s3Presigner;

    static S3Presigner s3Presigner() {
        return S3Presigner.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret")))
                .build();
    }

    private ResultActions uploadUrl(String role, String body) throws Exception {
        var req = post("/admin/banners/image-upload-url").header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", role);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(req);
    }

    @Test
    void 관리자는_배너_그림_업로드_주소를_받는다_JPG_기본() throws Exception {
        uploadUrl("ADMIN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageKey").value(matchesPattern("images/banners/" + UUID_PART + "\\.jpg")))
                .andExpect(jsonPath("$.imageUrl").value(matchesPattern("/images/banners/" + UUID_PART + "\\.jpg")))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.uploadUrl").value(containsString("freshlink-test-frontend")))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void PNG도_받는다() throws Exception {
        uploadUrl("ADMIN", "{\"contentType\":\"image/png\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(matchesPattern("/images/banners/" + UUID_PART + "\\.png")))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void JPG_PNG가_아니면_400() throws Exception {
        uploadUrl("ADMIN", "{\"contentType\":\"image/gif\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORE_IMAGE_TYPE_INVALID"));
    }

    @Test
    void 관리자가_아니면_403() throws Exception {
        for (String role : new String[] {"OWNER", "CUSTOMER", ""}) {
            uploadUrl(role, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }
}
