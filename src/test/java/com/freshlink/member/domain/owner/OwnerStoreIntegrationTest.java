package com.freshlink.member.domain.owner;

import com.freshlink.member.TestcontainersConfiguration;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 점주 내 점포 OW-01~03 (추가 명세 A15)을 실제 MySQL에서 확인한다.
 * 주소 서명은 테스트용 고정 자격으로 하고 S3에는 접속하지 않는다. 지역은 다른 테스트와 겹치지 않게 GANGWON을 쓴다
 */
@SpringBootTest(properties = "app.store-image.bucket=freshlink-test-frontend")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OwnerStoreIntegrationTest {

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

    /** 점주 회원과 그 점포를 넣고 점주 sub를 돌려준다 */
    private String ownerWithStore(String status) {
        String owner = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO member (id, role, email, created_at, updated_at) VALUES (?, 'OWNER', ?, NOW(6), NOW(6))",
                owner, "o" + uniq() + "@freshlink.test");
        String name = "내점포" + uniq();
        jdbc.update("INSERT INTO store (name, address, region, status, owner_member_id, created_at, updated_at) "
                + "VALUES (?, '강원 춘천시 중앙로 1', 'GANGWON', ?, ?, NOW(6), NOW(6))", name, status, owner);
        Long id = storeIdOf(owner);
        jdbc.update("UPDATE store SET store_code = ? WHERE id = ?", "S" + id, id);
        return owner;
    }

    private Long storeIdOf(String owner) {
        return jdbc.queryForObject("SELECT id FROM store WHERE owner_member_id = ?", Long.class, owner);
    }

    private ResultActions read(String owner, String role) throws Exception {
        var req = get("/owner/store").header("X-User-Id", owner);
        if (role != null) req.header("X-User-Role", role);
        return mockMvc.perform(req);
    }

    private ResultActions update(String owner, String role, String body) throws Exception {
        var req = patch("/owner/store").contentType(MediaType.APPLICATION_JSON).content(body).header("X-User-Id", owner);
        if (role != null) req.header("X-User-Role", role);
        return mockMvc.perform(req);
    }

    private ResultActions uploadUrl(String owner, String role, String body) throws Exception {
        var req = post("/owner/store/image-upload-url").header("X-User-Id", owner);
        if (role != null) req.header("X-User-Role", role);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(req);
    }

    private String key(Long storeId) {
        return "images/stores/" + storeId + "/" + UUID.randomUUID() + ".jpg";
    }

    // ---------------------------------------------------------------- OW-01

    @Test
    void 내_점포_조회_정해진_칸만() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        Long id = storeIdOf(owner);
        read(owner, "OWNER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(id))
                .andExpect(jsonPath("$.address").value("강원 춘천시 중앙로 1"))
                .andExpect(jsonPath("$.region").value("GANGWON"))
                .andExpect(jsonPath("$.latitude").value(nullValue()))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.storeCode").doesNotExist())
                .andExpect(jsonPath("$.ownerEmail").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    @Test
    void 점포가_없는_점주는_404() throws Exception {
        String owner = UUID.randomUUID().toString();
        read(owner, "OWNER").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        update(owner, "OWNER", "{\"description\":\"x\"}").andExpect(status().isNotFound());
        uploadUrl(owner, "OWNER", null).andExpect(status().isNotFound());
    }

    @Test
    void 점주가_아니면_403() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        for (String role : new String[] {"CUSTOMER", "ADMIN", null}) {
            read(owner, role).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            update(owner, role, "{\"description\":\"x\"}").andExpect(status().isForbidden());
            uploadUrl(owner, role, null).andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- OW-02

    @Test
    void 설명과_이미지를_바꾸고_빈_설명은_지운다() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        Long id = storeIdOf(owner);
        String key = key(id);
        update(owner, "OWNER", "{\"description\":\"  춘천 첫 매장  \",\"imageKey\":\"" + key + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("춘천 첫 매장"))
                .andExpect(jsonPath("$.imageUrl").value("/" + key));

        update(owner, "OWNER", "{\"description\":\"  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.imageUrl").value("/" + key));
        assertThat(jdbc.queryForObject("SELECT image_key FROM store WHERE id = ?", String.class, id)).isEqualTo(key);
    }

    @Test
    void 관리자만_바꾸는_칸을_보내면_400_아무것도_안_바뀐다() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        Long id = storeIdOf(owner);
        String nameBefore = jdbc.queryForObject("SELECT name FROM store WHERE id = ?", String.class, id);
        for (String body : new String[] {
                "{\"description\":\"x\",\"name\":\"바꾼이름\"}",
                "{\"description\":\"x\",\"address\":\"강원 원주시 1\"}",
                "{\"description\":\"x\",\"region\":\"SEOUL\"}",
                "{\"description\":\"x\",\"latitude\":37.5}",
                "{\"description\":\"x\",\"longitude\":127.0}"}) {
            update(owner, "OWNER", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(containsString("점주는 설명·이미지만 바꿀 수 있습니다")));
        }
        assertThat(jdbc.queryForObject("SELECT name FROM store WHERE id = ?", String.class, id)).isEqualTo(nameBefore);
        assertThat(jdbc.queryForObject("SELECT description FROM store WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    void 바꿀_칸이_없으면_400() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        update(owner, "OWNER", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("바꿀 필드가 하나도 없습니다")));
    }

    @Test
    void 설명이_500자를_넘으면_400() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        update(owner, "OWNER", "{\"description\":\"" + "가".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 다른_점포_키나_모양이_다른_키는_400() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        String other = ownerWithStore("ACTIVE");
        for (String bad : new String[] {key(storeIdOf(other)), "images/stores/" + storeIdOf(owner) + "/x.jpg", "a.jpg"}) {
            update(owner, "OWNER", "{\"imageKey\":\"" + bad + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("STORE_IMAGE_KEY_INVALID"));
        }
    }

    @Test
    void 등록_중_점포는_수정도_업로드_주소도_409() throws Exception {
        String owner = ownerWithStore("PENDING");
        update(owner, "OWNER", "{\"description\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_NOT_EDITABLE"));
        uploadUrl(owner, "OWNER", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_NOT_EDITABLE"));
    }

    @Test
    void 관리자와_점주가_각자_칸만_바꾼다() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        Long id = storeIdOf(owner);
        mockMvc.perform(patch("/admin/stores/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"관리자" + uniq() + "\",\"latitude\":37.881315,\"longitude\":127.729970}")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        String nameAfterAdmin = jdbc.queryForObject("SELECT name FROM store WHERE id = ?", String.class, id);

        update(owner, "OWNER", "{\"description\":\"점주 설명\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nameAfterAdmin))
                .andExpect(jsonPath("$.latitude").value(37.881315))
                .andExpect(jsonPath("$.description").value("점주 설명"));

        mockMvc.perform(patch("/admin/stores/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"강원 춘천시 중앙로 2\"}")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("점주 설명"));
    }

    // ---------------------------------------------------------------- OW-03

    @Test
    void 업로드_주소는_내_점포_키로_발급하고_형식이_없으면_JPG() throws Exception {
        String owner = ownerWithStore("ACTIVE");
        Long id = storeIdOf(owner);
        uploadUrl(owner, "OWNER", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageKey").value(matchesPattern("images/stores/" + id + "/[0-9a-f-]{36}\\.jpg")))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.uploadUrl").value(containsString("freshlink-test-frontend")));
        uploadUrl(owner, "OWNER", "{\"contentType\":\"image/png\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageKey").value(matchesPattern("images/stores/" + id + "/.*\\.png")));
        uploadUrl(owner, "OWNER", "{\"contentType\":\"image/gif\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORE_IMAGE_TYPE_INVALID"));
    }
}
