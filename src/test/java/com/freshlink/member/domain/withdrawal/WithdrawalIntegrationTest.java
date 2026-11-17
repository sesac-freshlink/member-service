package com.freshlink.member.domain.withdrawal;

import com.freshlink.member.TestcontainersConfiguration;
import com.freshlink.member.domain.member.MemberRole;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MB-05 고객 회원탈퇴 (추가 명세 A6·A7)를 실제 MySQL에서 확인한다. order·Cognito는 목이다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WithdrawalIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    WithdrawalService withdrawalService;
    @Autowired
    MemberWithdrawalRepository withdrawalRepository;
    @MockitoBean
    OrderActivity orderActivity;
    @MockitoBean
    CustomerAccounts customerAccounts;

    private String nick() {
        return "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }

    /** MB-02로 고객 프로필을 만든다 */
    private String customer(String nickname) throws Exception {
        String sub = UUID.randomUUID().toString();
        mockMvc.perform(post("/members/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김경우\",\"nickname\":\"" + nickname + "\",\"region\":\"BUSAN\","
                                + "\"termsAgreed\":true,\"privacyAgreed\":true}")
                        .header("X-User-Id", sub).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isCreated());
        return sub;
    }

    private ResultActions withdraw(String sub, String role) throws Exception {
        return mockMvc.perform(delete("/members/me").header("X-User-Id", sub).header("X-User-Role", role));
    }

    private int members(String sub) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE id = ?", Integer.class, sub);
    }

    private int history(String sub) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member_withdrawal WHERE member_id = ?", Integer.class, sub);
    }

    @Test
    void 진행_중_주문이_없으면_계정과_프로필을_지우고_이력을_남긴다_같은_닉네임으로_다시_가입할_수_있다() throws Exception {
        String nickname = nick();
        String sub = customer(nickname);
        when(orderActivity.activeOrders(sub)).thenReturn(0L);
        when(customerAccounts.delete(sub)).thenReturn(Optional.of("kim@test.com"));

        withdraw(sub, "CUSTOMER").andExpect(status().isNoContent());

        assertThat(members(sub)).isZero();
        var row = jdbc.queryForMap("SELECT email, role, withdrawn_by, withdrawn_at FROM member_withdrawal WHERE member_id = ?", sub);
        assertThat(row.get("email")).isEqualTo("kim@test.com");
        assertThat(row.get("role")).isEqualTo("CUSTOMER");
        assertThat(row.get("withdrawn_by")).isEqualTo("SELF");
        assertThat(row.get("withdrawn_at")).isNotNull();
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'member_withdrawal'", String.class))
                .doesNotContain("region");
        customer(nickname);
    }

    @Test
    void 진행_중_주문이_있으면_409이고_아무것도_지우지_않는다() throws Exception {
        String sub = customer(nick());
        when(orderActivity.activeOrders(sub)).thenReturn(2L);

        withdraw(sub, "CUSTOMER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_BLOCKED_ACTIVE_ORDER"));

        assertThat(members(sub)).isOne();
        assertThat(history(sub)).isZero();
        verify(customerAccounts, never()).delete(anyString());
    }

    @Test
    void 주문을_확인하지_못하면_503이고_탈퇴하지_않는다() throws Exception {
        String sub = customer(nick());
        when(orderActivity.activeOrders(sub)).thenThrow(new IllegalStateException("timeout"));

        withdraw(sub, "CUSTOMER").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_CHECK_UNAVAILABLE"));
        assertThat(members(sub)).isOne();
        verify(customerAccounts, never()).delete(anyString());
    }

    @Test
    void Cognito_삭제가_실패하면_502이고_프로필과_이력은_그대로() throws Exception {
        String sub = customer(nick());
        when(orderActivity.activeOrders(sub)).thenReturn(0L);
        when(customerAccounts.delete(sub)).thenThrow(CognitoIdentityProviderException.builder().message("x").build());

        withdraw(sub, "CUSTOMER").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_FAILED"));
        assertThat(members(sub)).isOne();
        assertThat(history(sub)).isZero();
    }

    @Test
    void 프로필이_없으면_404_고객이_아니면_403() throws Exception {
        withdraw(UUID.randomUUID().toString(), "CUSTOMER").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_NOT_FOUND"));
        withdraw(UUID.randomUUID().toString(), "OWNER").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 보관_기간_5년_경계_앞뒤로_지난_이력만_지운다() {
        String old = UUID.randomUUID().toString();
        String recent = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9));
        // 실제 저장 경로(JPA)로 넣고, 5년(1825일)에서 1시간씩 앞뒤라 시간대(9시간) 계산이 틀리면 결과가 바뀐다
        withdrawalRepository.save(MemberWithdrawal.self(old, "a@test.com", MemberRole.CUSTOMER,
                now.minusDays(1825).minusHours(1)));
        withdrawalRepository.save(MemberWithdrawal.self(recent, "a@test.com", MemberRole.CUSTOMER,
                now.minusDays(1825).plusHours(1)));

        withdrawalService.deleteExpiredHistory();

        assertThat(history(old)).isZero();
        assertThat(history(recent)).isOne();
    }

    @Test
    void 진행_중_주문_수가_음수면_503이고_탈퇴하지_않는다() throws Exception {
        String sub = customer(nick());
        when(orderActivity.activeOrders(sub)).thenReturn(-1L);

        withdraw(sub, "CUSTOMER").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_CHECK_UNAVAILABLE"));
        assertThat(members(sub)).isOne();
        verify(customerAccounts, never()).delete(anyString());
    }

    @Test
    void 점주_관리자_역할이_같이_있거나_DB_역할이_고객이_아니면_403이고_지우지_않는다() throws Exception {
        String sub = customer(nick());
        when(orderActivity.activeOrders(sub)).thenReturn(0L);

        withdraw(sub, "CUSTOMER,OWNER").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        withdraw(sub, "ADMIN,CUSTOMER").andExpect(status().isForbidden());

        jdbc.update("UPDATE member SET role = 'OWNER' WHERE id = ?", sub);
        withdraw(sub, "CUSTOMER").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(members(sub)).isOne();
        verify(customerAccounts, never()).delete(anyString());
    }

    @Test
    void 탈퇴한_회원_ID로_프로필을_다시_만들면_409() throws Exception {
        String sub = customer(nick());
        when(orderActivity.activeOrders(sub)).thenReturn(0L);
        when(customerAccounts.delete(sub)).thenReturn(Optional.of("kim@test.com"));
        withdraw(sub, "CUSTOMER").andExpect(status().isNoContent());

        // 탈퇴 전에 받은 토큰이 아직 살아 있는 경우
        mockMvc.perform(post("/members/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김경우\",\"nickname\":\"" + nick() + "\",\"region\":\"BUSAN\","
                                + "\"termsAgreed\":true,\"privacyAgreed\":true}")
                        .header("X-User-Id", sub).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWN_MEMBER"));
        assertThat(members(sub)).isZero();
    }
}
