package com.freshlink.member.domain.board;

import com.freshlink.member.TestcontainersConfiguration;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 게시판 공지사항·FAQ (추가 명세 A17)를 실제 MySQL에서 확인한다. 테스트마다 표를 비운다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BoardIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM notice");
        jdbc.update("DELETE FROM faq");
    }

    private ResultActions as(String role, MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req.header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", role));
    }

    private long notice(String title, String audience, boolean pinned) throws Exception {
        String body = as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"본문\\n둘째 줄\",\"audience\":\"" + audience
                                + "\",\"pinned\":" + pinned + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("noticeId").asLong();
    }

    private long faq(String question, String audience, int order) throws Exception {
        String body = as("ADMIN", post("/admin/faqs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"answer\":\"답\",\"audience\":\"" + audience
                                + "\",\"sortOrder\":" + order + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("faqId").asLong();
    }

    @Test
    void 고객은_ALL과_고객_공지만_점주는_ALL과_점주_공지만_고정_먼저_최신순() throws Exception {
        // 고정 공지를 가장 먼저(가장 오래전에) 써서, 최신순만으로는 맨 위에 올 수 없게 한다
        notice("고정 공지", "ALL", true);
        notice("모두 1", "ALL", false);
        notice("고객용", "CUSTOMER", false);
        notice("점주용", "OWNER", false);

        as("CUSTOMER", get("/members/notices")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].title").value("고정 공지"))
                .andExpect(jsonPath("$.content[0].pinned").value(true))
                .andExpect(jsonPath("$.content[1].title").value("고객용"))
                .andExpect(jsonPath("$.content[2].title").value("모두 1"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
        as("OWNER", get("/members/notices")).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[1].title").value("점주용"));
        as("ADMIN", get("/admin/notices")).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void 상세는_본문까지이고_내_대상이_아니거나_없으면_404() throws Exception {
        long forOwner = notice("점주용", "OWNER", false);
        as("OWNER", get("/members/notices/" + forOwner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("본문\n둘째 줄"))
                .andExpect(jsonPath("$.createdAt").exists());
        as("CUSTOMER", get("/members/notices/" + forOwner)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
        as("CUSTOMER", get("/members/notices/999999")).andExpect(status().isNotFound());
    }

    @Test
    void 관리자는_공지를_고치고_지운다_다른_역할은_403() throws Exception {
        long id = notice("처음 제목", "ALL", false);
        as("ADMIN", put("/admin/notices/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  바꾼 제목  \",\"content\":\"새 본문\",\"audience\":\"CUSTOMER\",\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바꾼 제목"))
                .andExpect(jsonPath("$.audience").value("CUSTOMER"))
                .andExpect(jsonPath("$.pinned").value(true));
        as("OWNER", get("/members/notices/" + id)).andExpect(status().isNotFound());

        as("CUSTOMER", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\",\"audience\":\"ALL\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as("OWNER", delete("/admin/notices/" + id)).andExpect(status().isForbidden());

        as("ADMIN", delete("/admin/notices/" + id)).andExpect(status().isNoContent());
        as("ADMIN", delete("/admin/notices/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void 공지_입력_규칙() throws Exception {
        as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \",\"content\":\"y\",\"audience\":\"ALL\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "가".repeat(101) + "\",\"content\":\"y\",\"audience\":\"ALL\"}"))
                .andExpect(status().isBadRequest());
        as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void FAQ는_대상별로_순서대로_보이고_관리자가_고치고_지운다() throws Exception {
        faq("두 번째", "ALL", 2);
        faq("첫 번째", "ALL", 1);
        long ownerFaq = faq("점주 질문", "OWNER", 0);
        faq("고객 질문", "CUSTOMER", 0);

        as("CUSTOMER", get("/members/faqs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].question").value("고객 질문"))
                .andExpect(jsonPath("$[1].question").value("첫 번째"))
                .andExpect(jsonPath("$[2].question").value("두 번째"));
        as("OWNER", get("/members/faqs")).andExpect(jsonPath("$[0].question").value("점주 질문"));
        as("ADMIN", get("/admin/faqs")).andExpect(jsonPath("$.length()").value(4));

        as("ADMIN", put("/admin/faqs/" + ownerFaq).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"바꾼 질문\",\"answer\":\"바꾼 답\",\"audience\":\"ALL\",\"sortOrder\":9}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sortOrder").value(9));
        as("CUSTOMER", get("/members/faqs")).andExpect(jsonPath("$.length()").value(4));
        as("ADMIN", delete("/admin/faqs/" + ownerFaq)).andExpect(status().isNoContent());
        as("ADMIN", put("/admin/faqs/" + ownerFaq).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"answer\":\"a\",\"audience\":\"ALL\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAQ_NOT_FOUND"));
        as("ADMIN", post("/admin/faqs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"answer\":\"a\",\"audience\":\"ALL\",\"sortOrder\":10000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 읽기는_고객과_점주만() throws Exception {
        as("ADMIN", get("/members/notices")).andExpect(status().isForbidden());
        mockMvc.perform(get("/members/faqs")).andExpect(status().isForbidden());
    }

    @Test
    void 없는_대상_값_긴_본문_음수_페이지_숫자가_아닌_ID는_400() throws Exception {
        // 읽을 수 없는 본문(없는 enum 값)은 공통 처리기가 400 BAD_REQUEST로 준다
        as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\",\"audience\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"" + "가".repeat(5001) + "\",\"audience\":\"ALL\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        as("ADMIN", post("/admin/faqs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"answer\":\"a\",\"audience\":\"ALL\",\"sortOrder\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        as("CUSTOMER", get("/members/notices").param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        as("ADMIN", get("/admin/notices").param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        as("CUSTOMER", get("/members/notices/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void 상한_안의_큰_페이지는_빈_목록_상한을_넘으면_400() throws Exception {
        notice("하나", "ALL", false);
        as("CUSTOMER", get("/members/notices").param("page", String.valueOf(BoardService.MAX_PAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
        as("CUSTOMER", get("/members/notices").param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        as("ADMIN", get("/admin/notices").param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_글을_고치거나_관리자_상세로_보면_404() throws Exception {
        as("ADMIN", put("/admin/notices/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\",\"audience\":\"ALL\",\"pinned\":false}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
        as("ADMIN", get("/admin/notices/999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
        as("ADMIN", put("/admin/faqs/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"answer\":\"a\",\"audience\":\"ALL\",\"sortOrder\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAQ_NOT_FOUND"));
    }

    @Test
    void 관리자_API는_역할_머리글이_없거나_관리자가_아니면_403() throws Exception {
        mockMvc.perform(get("/admin/notices")).andExpect(status().isForbidden());
        as("CUSTOMER", get("/admin/faqs")).andExpect(status().isForbidden());
        as("CUSTOMER", post("/admin/faqs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"answer\":\"a\",\"audience\":\"ALL\",\"sortOrder\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 본문의_태그는_그대로_글자로_저장하고_돌려준다() throws Exception {
        String body = as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"<b>제목</b>\",\"content\":\"<script>x</script> 본문\",\"audience\":\"ALL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("noticeId").asLong();
        as("CUSTOMER", get("/members/notices/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("<b>제목</b>"))
                .andExpect(jsonPath("$.content").value("<script>x</script> 본문"));
    }

    @Test
    void 제목_질문_앞뒤의_전각_공백은_지우고_길이를_센다() throws Exception {
        String title = "가".repeat(100);
        String body = as("ADMIN", post("/admin/notices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\u3000" + title + "\u2003\",\"content\":\"y\",\"audience\":\"ALL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("noticeId").asLong();
        as("CUSTOMER", get("/members/notices/" + id)).andExpect(jsonPath("$.title").value(title));

        String question = "질".repeat(200);
        as("ADMIN", post("/admin/faqs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\u2003" + question + "\u3000\",\"answer\":\"a\",\"audience\":\"ALL\",\"sortOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.question").value(question));
    }
}
