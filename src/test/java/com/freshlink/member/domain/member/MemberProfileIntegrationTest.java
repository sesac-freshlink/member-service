package com.freshlink.member.domain.member;

import com.freshlink.member.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MB-01 닉네임 중복 확인, MB-02 고객 프로필 등록, MB-03 내 정보 조회를 실제 MySQL에서 확인한다. 점포는 점포 검색 테스트와 겹치지 않는 지역(CHUNGBUK)에 넣는다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberProfileIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    private String sub() {
        return UUID.randomUUID().toString();
    }

    /** 테스트마다 겹치지 않는 닉네임 (영문 소문자 + 숫자 8자) */
    private String nick() {
        return "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }

    private String body(String name, String nickname, String region) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"")
                + ",\"nickname\":" + (nickname == null ? "null" : "\"" + nickname + "\"")
                + ",\"region\":" + (region == null ? "null" : "\"" + region + "\"") + "}";
    }

    private ResultActions register(String sub, String role, String body) throws Exception {
        return mockMvc.perform(post("/members/me").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-User-Id", sub).header("X-User-Role", role));
    }

    private ResultActions me(String sub, String role) throws Exception {
        return mockMvc.perform(get("/members/me").header("X-User-Id", sub).header("X-User-Role", role));
    }

    private ResultActions availability(String nickname) throws Exception {
        return mockMvc.perform(get("/members/nickname-availability").param("nickname", nickname));
    }

    // ---------------------------------------------------------------- MB-01

    @Test
    void 닉네임은_앞뒤_공백과_영문_대소문자를_무시하고_비교한다() throws Exception {
        String n = nick();
        register(sub(), "CUSTOMER", body("김경우", n, "SEOUL")).andExpect(status().isCreated());

        availability(n.toUpperCase()).andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(n.toUpperCase()))
                .andExpect(jsonPath("$.available").value(false));
        availability(" " + n + " ").andExpect(jsonPath("$.available").value(false));
        availability(nick()).andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 닉네임_형식이_틀리면_400() throws Exception {
        for (String bad : new String[] {"a", "abcdefghijk", "공백 있음", "특수!", ""}) {
            availability(bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/members/nickname-availability")).andExpect(status().isBadRequest());
        availability("한글닉네임1").andExpect(status().isOk());
    }

    @Test
    void 형식_오류_message는_칸_이름과_한국어_사유로_나온다() throws Exception {
        availability("a").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("nickname: 2~10자 한글·영문·숫자여야 합니다"));
        register(sub(), "CUSTOMER", body("김", nick(), "SEOUL")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: 2~30자 한글·영문이어야 합니다"));
        register(sub(), "CUSTOMER", body("김경우", nick(), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("region: 필수입니다"));
    }

    // ---------------------------------------------------------------- MB-02

    @Test
    void 프로필을_처음_저장하면_201이고_입력한_표기_그대로_돌려준다() throws Exception {
        String sub = sub();
        String n = "Fr" + nick().substring(1, 7);

        register(sub, "CUSTOMER", body("  김 경우  ", n, "BUSAN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(sub))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.name").value("김 경우"))
                .andExpect(jsonPath("$.nickname").value(n))
                .andExpect(jsonPath("$.region").value("BUSAN"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(".*\\+09:00$")));

        assertThat(jdbc.queryForObject("SELECT nickname_normalized FROM member WHERE id = ?", String.class, sub))
                .isEqualTo(n.toLowerCase());
        assertThat(jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, sub)).isNull();
    }

    @Test
    void 같은_sub로_다시_보내면_200이고_기존_프로필을_준다() throws Exception {
        String sub = sub();
        String n = nick();
        register(sub, "CUSTOMER", body("김경우", n, "SEOUL")).andExpect(status().isCreated());

        register(sub, "CUSTOMER", body("다른이름", nick(), "JEJU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김경우"))
                .andExpect(jsonPath("$.nickname").value(n))
                .andExpect(jsonPath("$.region").value("SEOUL"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE id = ?", Integer.class, sub)).isEqualTo(1);
    }

    @Test
    void 대소문자만_다른_닉네임은_409_NICKNAME_DUPLICATED() throws Exception {
        String n = nick();
        register(sub(), "CUSTOMER", body("김경우", n, "SEOUL")).andExpect(status().isCreated());

        register(sub(), "CUSTOMER", body("이태우", n.toUpperCase(), "SEOUL"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    void 같은_닉네임_동시_가입은_먼저_저장된_쪽만_성공() throws Exception {
        String n = nick();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Callable<Integer> attempt = () -> register(sub(), "CUSTOMER", body("김경우", n, "SEOUL"))
                    .andReturn().getResponse().getStatus();
            java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(attempt));
            }
            int created = 0;
            int conflict = 0;
            for (Future<Integer> f : results) {
                int code = f.get();
                if (code == 201) created++;
                if (code == 409) conflict++;
            }
            assertThat(created).isEqualTo(1);
            assertThat(conflict).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_sub로_동시에_보내면_하나만_201이고_나머지는_200() throws Exception {
        String sub = sub();
        String n = nick();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                results.add(pool.submit(() -> register(sub, "CUSTOMER", body("김경우", n, "SEOUL"))
                        .andReturn().getResponse().getStatus()));
            }
            java.util.List<Integer> codes = new java.util.ArrayList<>();
            for (Future<Integer> f : results) codes.add(f.get());
            assertThat(codes).as("응답 코드 %s", codes).containsOnlyOnce(201);
            assertThat(codes.stream().filter(c -> c == 200)).as("응답 코드 %s", codes).hasSize(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 고객이_아니면_403_FORBIDDEN() throws Exception {
        register(sub(), "OWNER", body("김경우", nick(), "SEOUL"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/members/me").contentType(MediaType.APPLICATION_JSON)
                        .content(body("김경우", nick(), "SEOUL")).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 입력_규칙을_어기면_400_VALIDATION_ERROR() throws Exception {
        String[] bad = {
                body("김", nick(), "SEOUL"),                 // 이름 2자 미만
                body("가".repeat(31), nick(), "SEOUL"),      // 이름 30자 초과
                body("김경우1", nick(), "SEOUL"),            // 이름에 숫자
                body(null, nick(), "SEOUL"),
                body("김경우", "닉 네임", "SEOUL"),           // 닉네임 공백
                body("김경우", "a", "SEOUL"),
                body("김경우", null, "SEOUL"),
                body("김경우", nick(), null),
        };
        for (String b : bad) {
            register(sub(), "CUSTOMER", b).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        register(sub(), "CUSTOMER", body("김경우", nick(), "MARS")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- MB-03

    @Test
    void 고객_내_정보() throws Exception {
        String sub = sub();
        String n = nick();
        register(sub, "CUSTOMER", body("김경우", n, "DAEGU")).andExpect(status().isCreated());

        me(sub, "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(sub))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.name").value("김경우"))
                .andExpect(jsonPath("$.nickname").value(n))
                .andExpect(jsonPath("$.region").value("DAEGU"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.storeId").doesNotExist());
    }

    @Test
    void 점주_내_정보는_점포_정보로() throws Exception {
        String sub = sub();
        String storeName = "점주테스트" + nick().substring(1, 5);
        jdbc.update("INSERT INTO member (id, role, email, created_at, updated_at) VALUES (?, 'OWNER', ?, NOW(6), NOW(6))",
                sub, sub.substring(0, 8) + "@freshlink.test");
        jdbc.update("INSERT INTO store (name, address, region, status, owner_member_id, created_at, updated_at) "
                + "VALUES (?, '충북 청주시 1', 'CHUNGBUK', 'ACTIVE', ?, NOW(6), NOW(6))", storeName, sub);
        Long storeId = jdbc.queryForObject("SELECT id FROM store WHERE owner_member_id = ?", Long.class, sub);
        jdbc.update("UPDATE store SET store_code = ? WHERE id = ?", "S" + storeId, storeId);

        me(sub, "OWNER").andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(sub))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.email").value(sub.substring(0, 8) + "@freshlink.test"))
                .andExpect(jsonPath("$.storeId").value(storeId))
                .andExpect(jsonPath("$.storeCode").value("S" + storeId))
                .andExpect(jsonPath("$.storeName").value(storeName))
                .andExpect(jsonPath("$.region").value("CHUNGBUK"))
                .andExpect(jsonPath("$.nickname").doesNotExist());
    }

    @Test
    void 프로필이_없으면_404_MEMBER_PROFILE_NOT_FOUND() throws Exception {
        me(sub(), "CUSTOMER").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_NOT_FOUND"));
    }

    @Test
    void 관리자나_역할_없음은_403() throws Exception {
        me(sub(), "ADMIN").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/members/me").header("X-User-Id", sub())).andExpect(status().isForbidden());
    }
}
