package com.freshlink.member.domain.member;

import com.freshlink.member.TestcontainersConfiguration;
import com.freshlink.member.domain.auth.SocialAccounts;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.freshlink.member.domain.store.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MB-01 닉네임 중복 확인, MB-02 고객 프로필 등록, MB-03 내 정보 조회, MB-04 회원정보 수정을 실제 MySQL에서 확인한다. 점포는 점포 검색 테스트와 겹치지 않는 지역(CHUNGBUK)에 넣는다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberProfileIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    /** 기본은 false(구글 중복 아님). 실제 Cognito에 접속하지 않는다 */
    @MockitoBean
    SocialAccounts socialAccounts;
    @Autowired
    MemberService memberService;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PlatformTransactionManager transactionManager;

    private String sub() {
        return UUID.randomUUID().toString();
    }

    /** 테스트마다 겹치지 않는 닉네임 (영문 소문자 + 숫자 8자) */
    private String nick() {
        return "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }

    private String body(String name, String nickname, String region) {
        return body(name, nickname, region, "true", "true");
    }

    /** 약관 동의 두 칸까지 (null이면 칸을 빼지 않고 null로 보낸다) */
    private String body(String name, String nickname, String region, String terms, String privacy) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"")
                + ",\"nickname\":" + (nickname == null ? "null" : "\"" + nickname + "\"")
                + ",\"region\":" + (region == null ? "null" : "\"" + region + "\"")
                + ",\"termsAgreed\":" + terms + ",\"privacyAgreed\":" + privacy + "}";
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

    private ResultActions update(String sub, String role, String body) throws Exception {
        return mockMvc.perform(patch("/members/me").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-User-Id", sub).header("X-User-Role", role));
    }

    // ---------------------------------------------------------------- A16 약관 동의

    @Test
    void 필수_약관_둘_다_동의해야_등록되고_버전과_시각이_남는다() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());
        var row = jdbc.queryForMap("SELECT terms_version, terms_agreed_at, privacy_version, privacy_agreed_at "
                + "FROM member WHERE id = ?", sub);
        assertThat(row.get("terms_version")).isEqualTo("2026-11-06");
        assertThat(row.get("privacy_version")).isEqualTo("2026-11-06");
        assertThat(row.get("terms_agreed_at")).isNotNull();
        assertThat(row.get("privacy_agreed_at")).isEqualTo(row.get("terms_agreed_at"));
    }

    @Test
    void 약관_동의가_없거나_하나만이면_400_프로필을_만들지_않는다() throws Exception {
        String[][] cases = {{"null", "null"}, {"false", "true"}, {"true", "false"}, {"false", "false"}};
        for (String[] c : cases) {
            String sub = sub();
            register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL", c[0], c[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("필수 약관에 동의해 주세요")));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE id = ?", Integer.class, sub)).isZero();
        }
        String sub = sub();
        register(sub, "CUSTOMER", "{\"name\":\"김경우\",\"nickname\":\"" + nick() + "\",\"region\":\"SEOUL\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미_프로필이_있으면_200이고_동의_칸을_덮어쓰지_않는다() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());
        jdbc.update("UPDATE member SET terms_version = 'old', terms_agreed_at = '2026-01-01 00:00:00' WHERE id = ?", sub);
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT terms_version FROM member WHERE id = ?", String.class, sub)).isEqualTo("old");
    }

    @Test
    void 내_정보_응답에는_동의_칸이_없다() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());
        me(sub, "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.termsVersion").doesNotExist())
                .andExpect(jsonPath("$.termsAgreed").doesNotExist());
    }

    // ---------------------------------------------------------------- C-01 구글 같은 이메일 막기

    @Test
    void 구글로_처음_들어온_계정과_같은_이메일_가입_계정이_있으면_409_EMAIL_SIGNUP_EXISTS() throws Exception {
        String sub = sub();
        org.mockito.Mockito.when(socialAccounts.emailSignupExistsFor(sub)).thenReturn(true);

        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_SIGNUP_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 이메일로 가입한 계정이 있습니다. 이메일로 로그인해 주세요"));
        me(sub, "CUSTOMER").andExpect(status().isNotFound());
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

    // ---------------------------------------------------------------- MB-04

    @Test
    void 세_칸을_모두_바꾸면_200이고_닉네임_비교값도_바뀐다() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());
        String created = jdbc.queryForObject("SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') FROM member WHERE id = ?",
                String.class, sub);
        String n = "N" + nick().substring(1);

        update(sub, "CUSTOMER", body(" 이태우 ", n, "BUSAN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(sub))
                .andExpect(jsonPath("$.name").value("이태우"))
                .andExpect(jsonPath("$.nickname").value(n))
                .andExpect(jsonPath("$.region").value("BUSAN"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(".*\\+09:00$")));

        assertThat(jdbc.queryForObject("SELECT nickname_normalized FROM member WHERE id = ?", String.class, sub))
                .isEqualTo(n.toLowerCase());
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') FROM member WHERE id = ?",
                String.class, sub)).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT updated_at > created_at FROM member WHERE id = ?", Boolean.class, sub))
                .isTrue();
        me(sub, "CUSTOMER").andExpect(jsonPath("$.name").value("이태우")).andExpect(jsonPath("$.region").value("BUSAN"));
    }

    @Test
    void 보낸_칸만_바뀌고_나머지는_그대로() throws Exception {
        String sub = sub();
        String n = nick();
        register(sub, "CUSTOMER", body("김경우", n, "SEOUL")).andExpect(status().isCreated());

        update(sub, "CUSTOMER", "{\"region\":\"JEJU\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김경우"))
                .andExpect(jsonPath("$.nickname").value(n))
                .andExpect(jsonPath("$.region").value("JEJU"));
        update(sub, "CUSTOMER", "{\"name\":\"김경우님\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김경우님"))
                .andExpect(jsonPath("$.nickname").value(n))
                .andExpect(jsonPath("$.region").value("JEJU"));
    }

    @Test
    void 대소문자만_바꾼_내_닉네임은_중복이_아니다() throws Exception {
        String sub = sub();
        String n = nick();
        register(sub, "CUSTOMER", body("김경우", n, "SEOUL")).andExpect(status().isCreated());

        update(sub, "CUSTOMER", "{\"nickname\":\"" + n.toUpperCase() + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(n.toUpperCase()));
    }

    @Test
    void 다른_회원_닉네임이면_409_NICKNAME_DUPLICATED() throws Exception {
        String taken = nick();
        register(sub(), "CUSTOMER", body("이태우", taken, "SEOUL")).andExpect(status().isCreated());
        String sub = sub();
        String mine = nick();
        register(sub, "CUSTOMER", body("김경우", mine, "SEOUL")).andExpect(status().isCreated());

        update(sub, "CUSTOMER", "{\"nickname\":\"" + taken.toUpperCase() + "\",\"region\":\"BUSAN\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
        me(sub, "CUSTOMER").andExpect(jsonPath("$.nickname").value(mine)).andExpect(jsonPath("$.region").value("SEOUL"));
    }

    @Test
    void 수정은_고객만_403_FORBIDDEN() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());
        for (String role : new String[] {"OWNER", "ADMIN", ""}) {
            update(sub, role, "{\"region\":\"BUSAN\"}").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mockMvc.perform(patch("/members/me").contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"BUSAN\"}")
                .header("X-User-Role", "CUSTOMER")).andExpect(status().isForbidden());
    }

    @Test
    void 수정할_프로필이_없으면_404_MEMBER_PROFILE_NOT_FOUND() throws Exception {
        update(sub(), "CUSTOMER", "{\"region\":\"BUSAN\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_NOT_FOUND"));
    }

    @Test
    void 수정_입력_규칙을_어기면_400_VALIDATION_ERROR() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());
        String[] bad = {
                "{\"name\":\"김\"}",                 // 이름 2자 미만
                "{\"name\":\"  \"}",                 // 공백만
                "{\"name\":\"김경우1\"}",            // 이름에 숫자
                "{\"nickname\":\"닉 네임\"}",        // 닉네임 공백
                "{\"nickname\":\"a\"}",
                "{}",                                  // 바꿀 칸이 없다
                body(null, null, null),
        };
        for (String b : bad) {
            update(sub, "CUSTOMER", b).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        update(sub, "CUSTOMER", "{\"region\":\"MARS\"}").andExpect(status().isBadRequest()); // 없는 시·도 (MB-02와 같다)
        me(sub, "CUSTOMER").andExpect(jsonPath("$.name").value("김경우")).andExpect(jsonPath("$.region").value("SEOUL"));
    }

    @Test
    void 이메일을_보내도_바뀌지_않는다() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());

        update(sub, "CUSTOMER", "{\"region\":\"BUSAN\",\"email\":\"x@test.com\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, sub)).isNull();
    }

    @Test
    void 다른_회원들이_동시에_같은_새_닉네임으로_바꾸면_하나만_200이고_나머지는_409() throws Exception {
        String[] subs = new String[4];
        for (int i = 0; i < subs.length; i++) {
            subs[i] = sub();
            register(subs[i], "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());
        }
        String n = nick();
        ExecutorService pool = Executors.newFixedThreadPool(subs.length);
        try {
            java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
            for (String s : subs) {
                results.add(pool.submit(() -> update(s, "CUSTOMER", "{\"nickname\":\"" + n + "\"}")
                        .andReturn().getResponse().getStatus()));
            }
            java.util.List<Integer> codes = new java.util.ArrayList<>();
            for (Future<Integer> f : results) codes.add(f.get());
            assertThat(codes).as("응답 코드 %s", codes).containsOnlyOnce(200);
            assertThat(codes).as("응답 코드 %s", codes).filteredOn(c -> c == 409).hasSize(subs.length - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE nickname_normalized = ?", Integer.class, n))
                .isEqualTo(1);
    }

    @Test
    void 지역만_바꾸는_수정은_그사이_바뀐_이름을_옛_값으로_덮지_않는다() throws Exception {
        String sub = sub();
        register(sub, "CUSTOMER", body("김경우", nick(), "SEOUL")).andExpect(status().isCreated());

        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            memberRepository.findById(sub).orElseThrow(); // 옛 값(김경우)을 먼저 읽어 둔다
            // 다른 요청이 그사이 이름을 바꿔 커밋한 상황 (다른 연결)
            ExecutorService other = Executors.newSingleThreadExecutor();
            try {
                other.submit(() -> jdbc.update("UPDATE member SET name = ? WHERE id = ?", "이태우", sub)).get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                other.shutdownNow();
            }
            memberService.updateProfile(sub, new ProfileUpdateRequest(null, null, Region.JEJU));
        });

        assertThat(jdbc.queryForObject("SELECT name FROM member WHERE id = ?", String.class, sub)).isEqualTo("이태우");
        assertThat(jdbc.queryForObject("SELECT region FROM member WHERE id = ?", String.class, sub)).isEqualTo("JEJU");
    }
}
