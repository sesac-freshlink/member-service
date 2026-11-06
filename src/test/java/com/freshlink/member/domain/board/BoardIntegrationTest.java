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
        notice("모두 1", "ALL", false);
        notice("고객용", "CUSTOMER", false);
        notice("점주용", "OWNER", false);
        notice("고정 공지", "ALL", true);

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
}
