package com.freshlink.member.domain.store;

import com.freshlink.member.TestcontainersConfiguration;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 점포 상세(공개)와 상세 값 넣기(AD-03)·이미지 업로드 주소 발급을 실제 MySQL에서 확인한다.
 * 주소 서명은 테스트용 고정 자격으로 하고 S3에는 접속하지 않는다. 지역은 다른 테스트와 겹치지 않게 JEJU를 쓴다
 */
@SpringBootTest(properties = "app.store-image.bucket=freshlink-test-frontend")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StoreDetailIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @TestBean
    S3Presigner s3Presigner;

    static S3Presigner s3Presigner() {
        return S3Presigner.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret")))
                .build();
    }

    private String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private long store(String status) {
        String name = "상세" + uniq();
        String owner = null;
        if (!"PENDING".equals(status)) {
            owner = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO member (id, role, email, created_at, updated_at) VALUES (?, 'OWNER', ?, NOW(6), NOW(6))",
                    owner, "o" + uniq() + "@freshlink.test");
        }
        jdbc.update("INSERT INTO store (name, address, region, status, owner_member_id, created_at, updated_at) "
                + "VALUES (?, '제주 첨단로 1', 'JEJU', ?, ?, NOW(6), NOW(6))", name, status, owner);
        Long id = jdbc.queryForObject("SELECT id FROM store WHERE name = ?", Long.class, name);
        jdbc.update("UPDATE store SET store_code = ? WHERE id = ?", "S" + id, id);
        return id;
    }

    private ResultActions adminPatch(long storeId, String body) throws Exception {
        return mockMvc.perform(patch("/admin/stores/" + storeId).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", "ADMIN"));
    }

    private ResultActions uploadUrl(String role, long storeId) throws Exception {
        var req = post("/admin/stores/" + storeId + "/image-upload-url").header("X-User-Id", UUID.randomUUID().toString());
        if (role != null) req.header("X-User-Role", role);
        return mockMvc.perform(req);
    }

    // ---------------------------------------------------------------- 점포 상세 (공개)

    @Test
    void 운영_중인_점포는_로그인_없이_상세를_본다_값이_없으면_null() throws Exception {
        long id = store("ACTIVE");

        mockMvc.perform(get("/stores/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(id))
                .andExpect(jsonPath("$.storeCode").value("S" + id))
                .andExpect(jsonPath("$.address").value("제주 첨단로 1"))
                .andExpect(jsonPath("$.region").value("JEJU"))
                .andExpect(jsonPath("$.latitude").value(nullValue()))
                .andExpect(jsonPath("$.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.ownerEmail").doesNotExist());
    }

    @Test
    void 등록_중이거나_운영_중단이거나_없는_점포는_404() throws Exception {
        for (long id : new long[]{store("PENDING"), store("INACTIVE"), 99_999_999L}) {
            mockMvc.perform(get("/stores/" + id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }
    }

    // ---------------------------------------------------------------- 상세 값 넣기 (AD-03)

    @Test
    void 관리자가_좌표_설명_이미지_키를_넣으면_상세에_나온다() throws Exception {
        long id = store("ACTIVE");
        String key = "images/stores/" + id + "/" + UUID.randomUUID() + ".jpg";

        adminPatch(id, "{\"latitude\":33.450701,\"longitude\":126.570667,\"description\":\"  제주 첫 매장 \","
                + "\"imageKey\":\"" + key + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/" + key));

        mockMvc.perform(get("/stores/" + id))
                .andExpect(jsonPath("$.latitude").value(33.450701))
                .andExpect(jsonPath("$.longitude").value(126.570667))
                .andExpect(jsonPath("$.description").value("제주 첫 매장"))
                .andExpect(jsonPath("$.imageUrl").value("/" + key));
    }

    @Test
    void 다른_점포나_모양이_다른_이미지_키는_400_STORE_IMAGE_KEY_INVALID() throws Exception {
        long id = store("ACTIVE");

        for (String key : new String[]{"images/stores/" + (id + 1) + "/" + UUID.randomUUID() + ".jpg",
                "images/stores/" + id + "/../other.jpg", "images/stores/" + id + "/" + UUID.randomUUID() + ".png"}) {
            adminPatch(id, "{\"imageKey\":\"" + key + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("STORE_IMAGE_KEY_INVALID"));
        }
        assertThat(jdbc.queryForObject("SELECT image_key FROM store WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    void 좌표_범위를_넘거나_설명이_500자를_넘으면_400() throws Exception {
        long id = store("ACTIVE");

        adminPatch(id, "{\"latitude\":91}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        adminPatch(id, "{\"longitude\":-180.5}").andExpect(status().isBadRequest());
        adminPatch(id, "{\"latitude\":33.1234567}").andExpect(status().isBadRequest());
        adminPatch(id, "{\"description\":\"" + "가".repeat(501) + "\"}").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- 이미지 업로드 주소

    @Test
    void 업로드_주소는_이_점포_키로_5분_PUT_서명이고_Content_Type이_고정된다() throws Exception {
        long id = store("ACTIVE");

        String body = uploadUrl("ADMIN", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageKey").value(matchesPattern(
                        "images/stores/" + id + "/[0-9a-f-]{36}\\.jpg")))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.imageUrl").value(matchesPattern("/images/stores/" + id + "/.*\\.jpg")))
                .andReturn().getResponse().getContentAsString();

        String url = com.jayway.jsonpath.JsonPath.read(body, "$.uploadUrl");
        String key = com.jayway.jsonpath.JsonPath.read(body, "$.imageKey");
        URI uri = URI.create(url);
        assertThat(uri.getHost()).startsWith("freshlink-test-frontend.s3");
        assertThat(uri.getPath()).isEqualTo("/" + key);
        assertThat(uri.getQuery()).contains("X-Amz-Expires=300").contains("content-type");
        // 발급한 키는 그대로 점포 수정에 넣을 수 있다
        adminPatch(id, "{\"imageKey\":\"" + key + "\"}").andExpect(status().isOk());
    }

    @Test
    void 업로드_주소는_관리자만_없는_점포는_404_등록_중이면_409() throws Exception {
        long active = store("ACTIVE");

        uploadUrl("OWNER", active).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        uploadUrl(null, active).andExpect(status().isForbidden());
        uploadUrl("ADMIN", 99_999_999L).andExpect(status().isNotFound());
        uploadUrl("ADMIN", store("PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_NOT_EDITABLE"));
    }
}
