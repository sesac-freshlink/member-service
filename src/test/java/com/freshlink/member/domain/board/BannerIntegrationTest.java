package com.freshlink.member.domain.board;

import com.freshlink.member.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 홈 메인 배너 (추가 명세 A18)를 실제 MySQL에서 확인한다. 테스트마다 표를 비운다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BannerIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM banner");
        jdbc.update("DELETE FROM notice");
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req.header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", role));
    }

    private ResultActions send(String role, MockHttpServletRequestBuilder req, String body) throws Exception {
        return as(role, req.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String title, String image, Long noticeId, Integer order, Boolean visible,
                               OffsetDateTime starts, OffsetDateTime ends) {
        return "{\"title\":" + quote(title) + ",\"imageUrl\":" + quote(image) + ",\"noticeId\":" + noticeId
                + ",\"sortOrder\":" + order + ",\"visible\":" + visible
                + ",\"startsAt\":" + quote(starts == null ? null : starts.toString())
                + ",\"endsAt\":" + quote(ends == null ? null : ends.toString()) + "}";
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private long banner(String title, int order) throws Exception {
        return banner(body(title, "/banners/sale.jpg", null, order, true, null, null));
    }

    private long banner(String requestBody) throws Exception {
        String res = send("ADMIN", post("/admin/banners"), requestBody)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("bannerId").asLong();
    }

    private long notice(String title) throws Exception {
        String res = send("ADMIN", post("/admin/notices"),
                "{\"title\":\"" + title + "\",\"content\":\"본문\",\"audience\":\"CUSTOMER\",\"pinned\":false}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("noticeId").asLong();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(KST);
    }

    // ---------------------------------------------------------------- 권한

    @Test
    void 홈_배너는_고객만_볼_수_있다() throws Exception {
        banner("11월 마감세일 주간", 0);
        as("CUSTOMER", get("/members/banners")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("11월 마감세일 주간"));
        for (String role : new String[] {"OWNER", "ADMIN", ""}) {
            as(role, get("/members/banners")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void 관리자_배너_API는_ADMIN만() throws Exception {
        long id = banner("픽업 신청 안내", 0);
        String req = body("x", "/banners/a.jpg", null, 0, true, null, null);
        for (String role : new String[] {"CUSTOMER", "OWNER", ""}) {
            as(role, get("/admin/banners")).andExpect(status().isForbidden());
            send(role, post("/admin/banners"), req).andExpect(status().isForbidden());
            send(role, put("/admin/banners/" + id), req).andExpect(status().isForbidden());
            as(role, delete("/admin/banners/" + id)).andExpect(status().isForbidden());
            send(role, put("/admin/banners/order"), "{\"bannerIds\":[" + id + "]}").andExpect(status().isForbidden());
        }
        as("ADMIN", get("/admin/banners")).andExpect(jsonPath("$.length()").value(1));
    }

    // ---------------------------------------------------------------- 입력

    @Test
    void 등록하면_앞뒤_공백을_지우고_비운_칸은_기본값으로_시각은_KST() throws Exception {
        send("ADMIN", post("/admin/banners"), body("  11월 마감세일 주간 ", "/banners/sale.jpg", null, null, null, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("11월 마감세일 주간"))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.noticeId").value(nullValue()))
                .andExpect(jsonPath("$.noticeTitle").value(nullValue()))
                .andExpect(jsonPath("$.startsAt").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value(endsWith("+09:00")));
    }

    @Test
    void 제목_순서_그림_경로_규칙을_어기면_400() throws Exception {
        String[] bad = {
                body(" ", "/banners/a.jpg", null, 0, true, null, null),
                body("가".repeat(51), "/banners/a.jpg", null, 0, true, null, null),
                body("제목", "/banners/a.jpg", null, -1, true, null, null),
                body("제목", "/banners/a.jpg", null, 10000, true, null, null),
                body("제목", "https://evil.example/a.jpg", null, 0, true, null, null),
                body("제목", "/banners/../stores/a.jpg", null, 0, true, null, null),
                body("제목", "/banners/a.gif", null, 0, true, null, null),
                body("제목", "/stores/a.jpg", null, 0, true, null, null),
                body("제목", "javascript:alert(1)", null, 0, true, null, null),
                body("제목", null, null, 0, true, null, null),
                body("제목", "/banners/a.jpg", 0L, 0, true, null, null),
        };
        for (String b : bad) {
            send("ADMIN", post("/admin/banners"), b)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        // 50자 딱 맞음, S3 연결 뒤 경로도 받는다
        banner(body("가".repeat(50), "/images/banners/1b2c-uuid.png", null, 9999, true, null, null));
        as("ADMIN", get("/admin/banners")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 노출_시작이_끝보다_늦거나_같으면_400() throws Exception {
        OffsetDateTime t = now();
        send("ADMIN", post("/admin/banners"), body("제목", "/banners/a.jpg", null, 0, true, t, t))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNER_PERIOD_INVALID"));
        send("ADMIN", post("/admin/banners"), body("제목", "/banners/a.jpg", null, 0, true, t.plusDays(1), t))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNER_PERIOD_INVALID"));
        long id = banner("제목", 0);
        send("ADMIN", put("/admin/banners/" + id), body("제목", "/banners/a.jpg", null, 0, true, t.plusDays(1), t))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNER_PERIOD_INVALID"));
    }

    @Test
    void 연결할_공지가_없으면_404_있으면_공지_제목을_같이_준다() throws Exception {
        send("ADMIN", post("/admin/banners"), body("제목", "/banners/a.jpg", 999999L, 0, true, null, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
        long noticeId = notice("11월 마감세일 주간 안내");
        send("ADMIN", post("/admin/banners"), body("제목", "/banners/a.jpg", noticeId, 0, true, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noticeId").value(noticeId))
                .andExpect(jsonPath("$.noticeTitle").value("11월 마감세일 주간 안내"));
        as("CUSTOMER", get("/members/banners")).andExpect(jsonPath("$[0].noticeId").value(noticeId))
                .andExpect(jsonPath("$[0].imageUrl").value("/banners/a.jpg"));
    }

    @Test
    void 연결한_공지를_지우면_배너는_남고_연결만_끊긴다() throws Exception {
        long noticeId = notice("안내");
        long id = banner(body("제목", "/banners/a.jpg", noticeId, 0, true, null, null));
        as("ADMIN", delete("/admin/notices/" + noticeId)).andExpect(status().isNoContent());
        as("ADMIN", get("/admin/banners")).andExpect(jsonPath("$[0].bannerId").value(id))
                .andExpect(jsonPath("$[0].noticeId").value(nullValue()))
                .andExpect(jsonPath("$[0].noticeTitle").value(nullValue()));
        as("CUSTOMER", get("/members/banners")).andExpect(jsonPath("$[0].noticeId").value(nullValue()));
    }

    // ---------------------------------------------------------------- 기간·순서·5장

    @Test
    void 홈에는_보이기_켜짐과_기간_안만_보이고_기간을_비우면_제한이_없다() throws Exception {
        OffsetDateTime t = now();
        banner(body("늘", "/banners/a.jpg", null, 0, true, null, null));
        banner(body("꺼짐", "/banners/a.jpg", null, 1, false, null, null));
        banner(body("아직", "/banners/a.jpg", null, 2, true, t.plusDays(1), null));
        banner(body("끝남", "/banners/a.jpg", null, 3, true, null, t.minusMinutes(1)));
        banner(body("기간 안", "/banners/a.jpg", null, 4, true, t.minusDays(1), t.plusDays(1)));
        banner(body("시작만", "/banners/a.jpg", null, 5, true, t.minusDays(1), null));
        banner(body("끝만", "/banners/a.jpg", null, 6, true, null, t.plusDays(1)));

        as("CUSTOMER", get("/members/banners")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].title").value("늘"))
                .andExpect(jsonPath("$[1].title").value("기간 안"))
                .andExpect(jsonPath("$[2].title").value("시작만"))
                .andExpect(jsonPath("$[3].title").value("끝만"))
                .andExpect(jsonPath("$[0].sortOrder").doesNotExist());
        // 관리자는 모두 본다
        as("ADMIN", get("/admin/banners")).andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    void 홈에는_순서대로_최대_5장_순서가_같으면_먼저_만든_것() throws Exception {
        banner("세번째", 2);
        banner("첫번째-가", 0);
        banner("첫번째-나", 0);
        banner("다섯번째", 4);
        banner("네번째", 3);
        banner("여섯번째", 5);
        banner("일곱번째", 6);

        as("CUSTOMER", get("/members/banners")).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].title").value("첫번째-가"))
                .andExpect(jsonPath("$[1].title").value("첫번째-나"))
                .andExpect(jsonPath("$[2].title").value("세번째"))
                .andExpect(jsonPath("$[3].title").value("네번째"))
                .andExpect(jsonPath("$[4].title").value("다섯번째"));
    }

    @Test
    void 배너가_없으면_빈_목록() throws Exception {
        as("CUSTOMER", get("/members/banners")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 순서_바꾸기는_지금_배너_전부를_받아_0부터_다시_매긴다() throws Exception {
        long a = banner("A", 0);
        long b = banner("B", 1);
        long c = banner("C", 2);
        send("ADMIN", put("/admin/banners/order"), "{\"bannerIds\":[" + c + "," + a + "," + b + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("C")).andExpect(jsonPath("$[0].sortOrder").value(0))
                .andExpect(jsonPath("$[1].title").value("A")).andExpect(jsonPath("$[1].sortOrder").value(1))
                .andExpect(jsonPath("$[2].title").value("B")).andExpect(jsonPath("$[2].sortOrder").value(2));
        as("CUSTOMER", get("/members/banners")).andExpect(jsonPath("$[0].title").value("C"));

        String[] bad = {
                "[" + a + "," + b + "]",                       // 빠짐
                "[" + a + "," + b + "," + c + "," + c + "]",   // 겹침
                "[" + a + "," + b + "," + 999999 + "]",        // 없는 ID
                "[" + a + "," + a + "," + b + "]",             // 수는 같지만 겹침·빠짐
        };
        for (String ids : bad) {
            send("ADMIN", put("/admin/banners/order"), "{\"bannerIds\":" + ids + "}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BANNER_ORDER_INVALID"));
        }
        send("ADMIN", put("/admin/banners/order"), "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 실패한 요청은 순서를 바꾸지 않는다
        as("ADMIN", get("/admin/banners")).andExpect(jsonPath("$[0].title").value("C"));
    }

    // ---------------------------------------------------------------- 고치기·지우기

    @Test
    void 고치면_모든_칸이_바뀌고_없는_배너는_404() throws Exception {
        long id = banner("옛 제목", 0);
        long noticeId = notice("안내");
        OffsetDateTime t = now();
        send("ADMIN", put("/admin/banners/" + id),
                body("새 제목", "/banners/new.png", noticeId, 3, false, t.minusDays(1), t.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.imageUrl").value("/banners/new.png"))
                .andExpect(jsonPath("$.noticeId").value(noticeId))
                .andExpect(jsonPath("$.sortOrder").value(3))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.startsAt").value(endsWith("+09:00")));
        as("CUSTOMER", get("/members/banners")).andExpect(jsonPath("$.length()").value(0));

        send("ADMIN", put("/admin/banners/999999"), body("x", "/banners/a.jpg", null, 0, true, null, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
        send("ADMIN", put("/admin/banners/" + id), body("x", "/banners/a.jpg", 999999L, 0, true, null, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
    }

    @Test
    void 지우면_바로_사라지고_두번째는_404() throws Exception {
        long id = banner("지울 배너", 0);
        as("ADMIN", delete("/admin/banners/" + id)).andExpect(status().isNoContent());
        as("ADMIN", get("/admin/banners")).andExpect(jsonPath("$.length()").value(0));
        as("ADMIN", delete("/admin/banners/" + id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
    }
}
