package com.freshlink.member.domain.admin;

import com.freshlink.member.TestcontainersConfiguration;
import com.freshlink.member.domain.auth.OwnerAccounts;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AD-01 점포·점주 등록을 실제 MySQL에서 확인한다. Cognito는 목이라 실제로 접속하지 않는다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StoreRegistrationIntegrationTest {

    private static final String PASSWORD = "Abcd!234";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    OwnerAccounts ownerAccounts;

    String name;
    String email;

    @BeforeEach
    void setUp() {
        String u = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        name = "등록" + u;
        email = "owner" + u + "@freshlink.test";
        when(ownerAccounts.emailExists(anyString())).thenReturn(false);
    }

    private String body(String name, String email, String password) {
        return "{\"name\":\"" + name + "\",\"address\":\"제주 제주시 1\",\"region\":\"JEJU\","
                + "\"ownerEmail\":\"" + email + "\",\"initialPassword\":\"" + password + "\"}";
    }

    private ResultActions register(String role, String body) throws Exception {
        return mockMvc.perform(post("/admin/stores").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", role));
    }

    private int storeCount(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE name = ?", Integer.class, name);
    }

    @Test
    void 등록하면_201이고_ACTIVE_점포와_점주_회원이_연결된다() throws Exception {
        String sub = UUID.randomUUID().toString();
        when(ownerAccounts.createOwner(eq(email), eq(PASSWORD), anyLong())).thenReturn(sub);

        register("ADMIN", body("  " + name + "  ", email, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.address").value("제주 제주시 1"))
                .andExpect(jsonPath("$.region").value("JEJU"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownerEmail").value(email))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(".*\\+09:00$")));

        Map<String, Object> store = jdbc.queryForMap("SELECT * FROM store WHERE name = ?", name);
        long storeId = ((Number) store.get("id")).longValue();
        assertThat(store).containsEntry("status", "ACTIVE").containsEntry("owner_member_id", sub)
                .containsEntry("store_code", "S" + storeId);
        assertThat(jdbc.queryForMap("SELECT role, email, name, nickname FROM member WHERE id = ?", sub))
                .containsEntry("role", "OWNER").containsEntry("email", email)
                .containsEntry("name", null).containsEntry("nickname", null);
        // Cognito custom:storeId = DB 점포 ID
        verify(ownerAccounts).createOwner(email, PASSWORD, storeId);
    }

    @Test
    void 같은_점포명이면_409이고_Cognito를_부르지_않는다() throws Exception {
        when(ownerAccounts.createOwner(anyString(), anyString(), anyLong())).thenReturn(UUID.randomUUID().toString());
        register("ADMIN", body(name, email, PASSWORD)).andExpect(status().isCreated());

        register("ADMIN", body(name, "other" + email, PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_NAME_DUPLICATED"));
        verify(ownerAccounts, never()).createOwner(eq("other" + email), anyString(), anyLong());
    }

    @Test
    void Cognito에_같은_이메일이_있으면_409이고_점포를_만들지_않는다() throws Exception {
        when(ownerAccounts.emailExists(email)).thenReturn(true);

        register("ADMIN", body(name, email, PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_EMAIL_DUPLICATED"));
        assertThat(storeCount(name)).isZero();
        verify(ownerAccounts, never()).createOwner(anyString(), anyString(), anyLong());
    }

    @Test
    void Cognito_계정_생성이_실패하면_500이고_PENDING_점포를_지운다() throws Exception {
        when(ownerAccounts.createOwner(anyString(), anyString(), anyLong())).thenThrow(new RuntimeException("cognito down"));

        register("ADMIN", body(name, email, PASSWORD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORE_REGISTRATION_FAILED"));
        assertThat(storeCount(name)).isZero();
        verify(ownerAccounts, never()).delete(anyString());
    }

    @Test
    void 계정을_만든_뒤_DB_저장이_실패하면_계정과_점포를_모두_지운다() throws Exception {
        // 같은 sub의 회원이 이미 있어 점주 회원 저장(4단계)이 실패하는 상황
        String sub = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO member (id, role, created_at, updated_at) VALUES (?, 'CUSTOMER', NOW(6), NOW(6))", sub);
        when(ownerAccounts.createOwner(anyString(), anyString(), anyLong())).thenReturn(sub);

        register("ADMIN", body(name, email, PASSWORD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORE_REGISTRATION_FAILED"));
        assertThat(storeCount(name)).isZero();
        verify(ownerAccounts).delete(email);
        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE id = ?", String.class, sub)).isEqualTo("CUSTOMER");
    }

    @Test
    void 정리까지_실패해도_500이고_등록_완료로_보이지_않는다() throws Exception {
        String sub = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO member (id, role, created_at, updated_at) VALUES (?, 'CUSTOMER', NOW(6), NOW(6))", sub);
        when(ownerAccounts.createOwner(anyString(), anyString(), anyLong())).thenReturn(sub);
        doThrow(new RuntimeException("delete failed")).when(ownerAccounts).delete(any());

        register("ADMIN", body(name, email, PASSWORD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORE_REGISTRATION_FAILED"));
        assertThat(storeCount(name)).isZero();
    }

    @Test
    void 입력_규칙을_어기면_400() throws Exception {
        String[] bad = {
                body(name, email, "short1!"),          // 8자 미만
                body(name, email, "abcd!234"),         // 대문자 없음
                body(name, email, "ABCD!234"),         // 소문자 없음
                body(name, email, "Abcd!efg"),         // 숫자 없음
                body(name, email, "Abcd1234"),         // 특수문자 없음
                body(name, "not-an-email", PASSWORD),
                body("가", email, PASSWORD),
                "{\"name\":\"" + name + "\",\"region\":\"JEJU\",\"ownerEmail\":\"" + email + "\",\"initialPassword\":\"" + PASSWORD + "\"}",
        };
        for (String b : bad) {
            register("ADMIN", b).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Abcd"))));
        }
        verify(ownerAccounts, never()).createOwner(anyString(), anyString(), anyLong());
    }

    @Test
    void 관리자가_아니면_403() throws Exception {
        register("OWNER", body(name, email, PASSWORD)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
