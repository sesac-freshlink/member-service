package com.freshlink.member.domain.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 점주 Cognito 계정 처리. 실제 Cognito에 접속하지 않는다 (SDK 클라이언트 목) */
class CognitoOwnerAccountsTest {

    private static final String POOL = "ap-northeast-2_testpool";

    CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
    CognitoOwnerAccounts accounts = new CognitoOwnerAccounts(client, POOL);

    @Test
    void 이메일로_계정을_찾는다_미인증_계정도_있다고_본다() {
        when(client.listUsers(any(ListUsersRequest.class))).thenReturn(ListUsersResponse.builder()
                .users(UserType.builder().username("u").userStatus("UNCONFIRMED").build()).build());

        assertThat(accounts.emailExists("owner9@freshlink.test")).isTrue();

        ArgumentCaptor<ListUsersRequest> req = ArgumentCaptor.forClass(ListUsersRequest.class);
        verify(client).listUsers(req.capture());
        assertThat(req.getValue().userPoolId()).isEqualTo(POOL);
        assertThat(req.getValue().filter()).isEqualTo("email = \"owner9@freshlink.test\"");
    }

    @Test
    void 계정이_없으면_false() {
        when(client.listUsers(any(ListUsersRequest.class))).thenReturn(ListUsersResponse.builder().build());

        assertThat(accounts.emailExists("none@freshlink.test")).isFalse();
    }

    @Test
    void 점주_계정은_인증_완료_storeId_영구_비밀번호_OWNER_그룹으로_만들고_sub를_돌려준다() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(AdminCreateUserResponse.builder()
                .user(UserType.builder().username("owner9@freshlink.test")
                        .attributes(AttributeType.builder().name("sub").value("sub-123").build()).build())
                .build());

        String sub = accounts.createOwner("owner9@freshlink.test", "Abcd!234", 9L);

        assertThat(sub).isEqualTo("sub-123");
        var order = inOrder(client);
        ArgumentCaptor<AdminCreateUserRequest> create = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        order.verify(client).adminCreateUser(create.capture());
        ArgumentCaptor<AdminSetUserPasswordRequest> pw = ArgumentCaptor.forClass(AdminSetUserPasswordRequest.class);
        order.verify(client).adminSetUserPassword(pw.capture());
        ArgumentCaptor<AdminAddUserToGroupRequest> group = ArgumentCaptor.forClass(AdminAddUserToGroupRequest.class);
        order.verify(client).adminAddUserToGroup(group.capture());

        AdminCreateUserRequest c = create.getValue();
        assertThat(c.userPoolId()).isEqualTo(POOL);
        assertThat(c.username()).isEqualTo("owner9@freshlink.test");
        assertThat(c.messageAction()).isEqualTo(MessageActionType.SUPPRESS);
        assertThat(c.userAttributes()).extracting(AttributeType::name, AttributeType::value).contains(
                org.assertj.core.groups.Tuple.tuple("email", "owner9@freshlink.test"),
                org.assertj.core.groups.Tuple.tuple("email_verified", "true"),
                org.assertj.core.groups.Tuple.tuple("custom:storeId", "9"));
        assertThat(pw.getValue().permanent()).isTrue();
        assertThat(pw.getValue().password()).isEqualTo("Abcd!234");
        assertThat(group.getValue().groupName()).isEqualTo("OWNER");
    }

    @Test
    void 계정을_지운다() {
        accounts.delete("owner9@freshlink.test");

        ArgumentCaptor<AdminDeleteUserRequest> req = ArgumentCaptor.forClass(AdminDeleteUserRequest.class);
        verify(client).adminDeleteUser(req.capture());
        assertThat(req.getValue().username()).isEqualTo("owner9@freshlink.test");
        assertThat(req.getValue().userPoolId()).isEqualTo(POOL);
    }

    @Test
    void 비밀번호_설정이_실패하면_만든_계정을_지우고_예외를_던진다() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(AdminCreateUserResponse.builder()
                .user(UserType.builder().username("owner9@freshlink.test")
                        .attributes(AttributeType.builder().name("sub").value("sub-123").build()).build())
                .build());
        when(client.adminSetUserPassword(any(AdminSetUserPasswordRequest.class)))
                .thenThrow(new RuntimeException("InvalidPasswordException"));

        assertThatThrownBy(() -> accounts.createOwner("owner9@freshlink.test", "Abcd!234", 9L))
                .hasMessageContaining("InvalidPassword");
        verify(client).adminDeleteUser(any(AdminDeleteUserRequest.class));
    }

    @Test
    void User_Pool_ID가_없으면_부르지_않고_실패한다() {
        CognitoOwnerAccounts noPool = new CognitoOwnerAccounts(client, "");

        assertThatThrownBy(() -> noPool.emailExists("a@b.c")).isInstanceOf(IllegalStateException.class);
    }
}
