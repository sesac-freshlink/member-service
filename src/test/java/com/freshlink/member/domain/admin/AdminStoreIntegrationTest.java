package com.freshlink.member.domain.admin;

import com.freshlink.member.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AD-02 점포 목록·AD-03 점포 수정을 실제 MySQL에서 확인한다.
 * 다른 테스트도 점포를 넣으므로 목록 테스트는 등록일을 먼 미래로 넣어 맨 앞에 오게 하고,
 * 점포 검색 테스트와 겹치지 않게 지역은 SEJONG을 쓴다
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminStoreIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    private String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    /** 점포(와 ACTIVE·INACTIVE면 점주 회원)를 넣는다. createdAt은 'yyyy-MM-dd HH:mm:ss' */
    private long store(String name, String status, String createdAt) {
        String owner = null;
        if (!"PENDING".equals(status)) {
            owner = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO member (id, role, email, created_at, updated_at) VALUES (?, 'OWNER', ?, NOW(6), NOW(6))",
                    owner, "o" + uniq() + "@freshlink.test");
        }
        jdbc.update("INSERT INTO store (name, address, region, status, owner_member_id, created_at, updated_at) "
                + "VALUES (?, '세종 한누리대로 1', 'SEJONG', ?, ?, ?, ?)", name, status, owner, createdAt, createdAt);
        Long id = jdbc.queryForObject("SELECT id FROM store WHERE name = ?", Long.class, name);
        jdbc.update("UPDATE store SET store_code = ? WHERE id = ?", "S" + id, id);
        return id;
    }

    private String ownerEmailOf(long storeId) {
        return jdbc.queryForObject("SELECT m.email FROM store s JOIN member m ON m.id = s.owner_member_id WHERE s.id = ?",
                String.class, storeId);
    }

    private ResultActions list(String role, String page) throws Exception {
        var req = get("/admin/stores").header("X-User-Id", UUID.randomUUID().toString());
        if (role != null) req.header("X-User-Role", role);
        if (page != null) req.param("page", page);
        return mockMvc.perform(req);
    }

    private ResultActions update(String role, long storeId, String body) throws Exception {
        var req = patch("/admin/stores/" + storeId).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-User-Id", UUID.randomUUID().toString());
        if (role != null) req.header("X-User-Role", role);
        return mockMvc.perform(req);
    }

    // ---------------------------------------------------------------- AD-02

    @Test
    void 모든_상태를_등록일_최신순으로_페이지_형식으로_내린다() throws Exception {
        long older = store("목록A" + uniq(), "ACTIVE", "2099-01-01 10:00:00");
        long pending = store("목록B" + uniq(), "PENDING", "2099-01-02 10:00:00");
        long newest = store("목록C" + uniq(), "INACTIVE", "2099-01-03 10:00:00");

        list("ADMIN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.content[0].storeId").value(newest))
                .andExpect(jsonPath("$.content[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.content[0].ownerEmail").value(ownerEmailOf(newest)))
                .andExpect(jsonPath("$.content[1].storeId").value(pending))
                .andExpect(jsonPath("$.content[1].status").value("PENDING"))
                .andExpect(jsonPath("$.content[1].ownerEmail").isEmpty())
                .andExpect(jsonPath("$.content[2].storeId").value(older))
                .andExpect(jsonPath("$.content[2].storeCode").value("S" + older))
                .andExpect(jsonPath("$.content[2].address").value("세종 한누리대로 1"))
                .andExpect(jsonPath("$.content[2].region").value("SEJONG"))
                .andExpect(jsonPath("$.content[2].createdAt").value("2099-01-01T10:00:00+09:00"));
    }

    @Test
    void 한_페이지는_20개이고_다음_페이지로_넘어간다() throws Exception {
        for (int i = 0; i < 21; i++) {
            store("페이지" + uniq(), "ACTIVE", String.format("2098-01-01 10:%02d:00", i));
        }
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM store", Integer.class);

        list("ADMIN", "0").andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.totalPages").value((total + 19) / 20));
        list("ADMIN", "1").andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        list("ADMIN", "-1").andExpect(status().isBadRequest());
    }

    @Test
    void 관리자가_아니면_403() throws Exception {
        list("OWNER", null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        list(null, null).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- AD-03

    @Test
    void 보낸_필드만_바꾸고_ID_코드_점주_상태는_그대로() throws Exception {
        long id = store("수정전" + uniq(), "ACTIVE", "2026-10-20 10:00:00");
        String owner = jdbc.queryForObject("SELECT owner_member_id FROM store WHERE id = ?", String.class, id);
        String newName = "수정후" + uniq();

        update("ADMIN", id, "{\"name\":\"  " + newName + "  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(id))
                .andExpect(jsonPath("$.storeCode").value("S" + id))
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.address").value("세종 한누리대로 1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownerEmail").value(ownerEmailOf(id)))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(".*\\+09:00$")));

        update("ADMIN", id, "{\"address\":\"부산 해운대구 2\",\"region\":\"BUSAN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.address").value("부산 해운대구 2"))
                .andExpect(jsonPath("$.region").value("BUSAN"));

        assertThat(jdbc.queryForObject("SELECT owner_member_id FROM store WHERE id = ?", String.class, id)).isEqualTo(owner);
        assertThat(jdbc.queryForObject("SELECT status FROM store WHERE id = ?", String.class, id)).isEqualTo("ACTIVE");
        // 점주 표시 이름은 바뀐 점포명을 따른다 (MB-03)
        mockMvc.perform(get("/members/me").header("X-User-Id", owner).header("X-User-Role", "OWNER"))
                .andExpect(jsonPath("$.storeName").value(newName));
    }

    @Test
    void 바꿀_필드가_없거나_규칙을_어기면_400() throws Exception {
        long id = store("규칙" + uniq(), "ACTIVE", "2026-10-20 10:00:00");

        for (String bad : new String[] {"{}", "{\"name\":\"가\"}", "{\"name\":\"" + "가".repeat(31) + "\"}",
                "{\"address\":\"  \"}", "{\"name\":null,\"address\":null,\"region\":null}"}) {
            update("ADMIN", id, bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        update("ADMIN", id, "{\"region\":\"MARS\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 수정_형식_오류_message는_칸_이름과_한국어_사유로_나온다() throws Exception {
        long id = store("문구" + uniq(), "ACTIVE", "2026-10-20 10:00:00");

        update("ADMIN", id, "{\"name\":\"가\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: 2~30자여야 합니다"));
    }

    @Test
    void 없는_점포_404_등록_중_점포_409_이름_중복_409() throws Exception {
        long pending = store("등록중" + uniq(), "PENDING", "2026-10-20 10:00:00");
        String taken = "있는이름" + uniq();
        store(taken, "ACTIVE", "2026-10-20 10:00:00");
        long active = store("바꿀점포" + uniq(), "ACTIVE", "2026-10-20 10:00:00");

        update("ADMIN", Long.MAX_VALUE, "{\"address\":\"x\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        update("ADMIN", pending, "{\"address\":\"x\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_NOT_EDITABLE"));
        update("ADMIN", active, "{\"name\":\"" + taken + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_NAME_DUPLICATED"));
        // 자기 이름 그대로 보내는 건 중복이 아니다
        String own = jdbc.queryForObject("SELECT name FROM store WHERE id = ?", String.class, active);
        update("ADMIN", active, "{\"name\":\"" + own + "\"}").andExpect(status().isOk());
    }

    @Test
    void 같은_점포를_동시에_다른_필드로_고쳐도_둘_다_남는다() throws Exception {
        long id = store("동시" + uniq(), "ACTIVE", "2026-10-20 10:00:00");
        String newName = "동시바꿈" + uniq();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> update("ADMIN", id, "{\"name\":\"" + newName + "\"}").andReturn().getResponse().getStatus());
            var b = pool.submit(() -> update("ADMIN", id, "{\"address\":\"세종 바뀐주소 9\"}").andReturn().getResponse().getStatus());
            assertThat(a.get()).isEqualTo(200);
            assertThat(b.get()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT name FROM store WHERE id = ?", String.class, id)).isEqualTo(newName);
        assertThat(jdbc.queryForObject("SELECT address FROM store WHERE id = ?", String.class, id)).isEqualTo("세종 바뀐주소 9");
    }

    @Test
    void 수정도_관리자만() throws Exception {
        long id = store("권한" + uniq(), "ACTIVE", "2026-10-20 10:00:00");
        update("OWNER", id, "{\"address\":\"x\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
