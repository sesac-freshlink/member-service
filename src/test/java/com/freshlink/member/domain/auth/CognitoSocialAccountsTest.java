package com.freshlink.member.domain.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 구글 계정과 같은 이메일의 가입 계정 찾기. 실제 Cognito에 접속하지 않는다 (SDK 클라이언트 목) */
class CognitoSocialAccountsTest {

    private static final String POOL = "ap-northeast-2_testpool";
    private static final String SUB = "aaaaaaaa-1111-2222-3333-444444444444";

    CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
    CognitoSocialAccounts accounts = new CognitoSocialAccounts(client, POOL);

    @Test
    void 구글_계정이고_같은_이메일의_이메일_가입_계정이_있으면_true() {
        stubSub(user("google_123", "EXTERNAL_PROVIDER", "kim@gmail.com"));
        stubEmail(user("google_123", "EXTERNAL_PROVIDER", "kim@gmail.com"), user("sub-email", "CONFIRMED", "kim@gmail.com"));

        assertThat(accounts.emailSignupExistsFor(SUB)).isTrue();

        ArgumentCaptor<ListUsersRequest> req = ArgumentCaptor.forClass(ListUsersRequest.class);
        verify(client, times(2)).listUsers(req.capture());
        assertThat(req.getAllValues().get(0).filter()).isEqualTo("sub = \"" + SUB + "\"");
        assertThat(req.getAllValues().get(0).userPoolId()).isEqualTo(POOL);
        assertThat(req.getAllValues().get(1).filter()).isEqualTo("email = \"kim@gmail.com\"");
    }

    @Test
    void 미인증_이메일_가입_계정도_있다고_본다() {
        stubSub(user("google_123", "EXTERNAL_PROVIDER", "kim@gmail.com"));
        stubEmail(user("google_123", "EXTERNAL_PROVIDER", "kim@gmail.com"), user("sub-email", "UNCONFIRMED", "kim@gmail.com"));

        assertThat(accounts.emailSignupExistsFor(SUB)).isTrue();
    }

    @Test
    void 같은_이메일이_구글_계정뿐이면_false() {
        stubSub(user("google_123", "EXTERNAL_PROVIDER", "kim@gmail.com"));
        stubEmail(user("google_123", "EXTERNAL_PROVIDER", "kim@gmail.com"));

        assertThat(accounts.emailSignupExistsFor(SUB)).isFalse();
    }

    @Test
    void 이메일_가입_계정이면_이메일로_찾지_않고_false() {
        stubSub(user("sub-email", "CONFIRMED", "kim@gmail.com"));

        assertThat(accounts.emailSignupExistsFor(SUB)).isFalse();
        verify(client, times(1)).listUsers(any(ListUsersRequest.class));
    }

    @Test
    void User_Pool_ID가_없으면_확인하지_않고_false() {
        CognitoSocialAccounts noPool = new CognitoSocialAccounts(client, "");

        assertThat(noPool.emailSignupExistsFor(SUB)).isFalse();
        verify(client, never()).listUsers(any(ListUsersRequest.class));
    }

    private void stubSub(UserType... users) {
        when(client.listUsers(argThat((ListUsersRequest r) -> r != null && r.filter().startsWith("sub ="))))
                .thenReturn(ListUsersResponse.builder().users(users).build());
    }

    private void stubEmail(UserType... users) {
        when(client.listUsers(argThat((ListUsersRequest r) -> r != null && r.filter().startsWith("email ="))))
                .thenReturn(ListUsersResponse.builder().users(users).build());
    }

    private static UserType user(String username, String status, String email) {
        return UserType.builder().username(username).userStatus(status)
                .attributes(AttributeType.builder().name("email").value(email).build()).build();
    }
}
