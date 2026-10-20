package com.freshlink.member.domain.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;

/**
 * Cognito 관리자 API로 점주 계정을 다룬다. 권한은 member IRSA Role (이 User Pool만).
 * 사용자 이름은 이메일이다(User Pool의 username 속성이 email). 비밀번호는 로그에 남기지 않는다
 */
public class CognitoOwnerAccounts implements OwnerAccounts {

    private static final Logger log = LoggerFactory.getLogger(CognitoOwnerAccounts.class);
    private static final String OWNER_GROUP = "OWNER";

    private final CognitoIdentityProviderClient client;
    private final String userPoolId;

    public CognitoOwnerAccounts(CognitoIdentityProviderClient client, String userPoolId) {
        this.client = client;
        this.userPoolId = userPoolId;
    }

    @Override
    public boolean emailExists(String email) {
        requirePool();
        return !client.listUsers(ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .filter("email = \"" + email.replace("\"", "") + "\"")
                .limit(1)
                .build()).users().isEmpty();
    }

    @Override
    public String createOwner(String email, String password, long storeId) {
        requirePool();
        AdminCreateUserResponse created = client.adminCreateUser(AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .messageAction(MessageActionType.SUPPRESS)   // 초대 메일을 보내지 않는다 (비밀번호는 관리자가 전달)
                .userAttributes(
                        attr("email", email),
                        attr("email_verified", "true"),      // MVP 점주 테스트 계정은 인증 완료로 만든다 (명세 M6)
                        attr("custom:storeId", String.valueOf(storeId)))
                .build());
        try {
            client.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId).username(email).password(password).permanent(true).build());
            client.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId).username(email).groupName(OWNER_GROUP).build());
        } catch (RuntimeException e) {
            // 계정만 만들어지고 비밀번호·그룹이 빠진 상태로 남기지 않는다
            safeDelete(email);
            throw e;
        }
        String sub = created.user().attributes().stream()
                .filter(a -> "sub".equals(a.name())).map(AttributeType::value).findFirst()
                .orElseThrow(() -> new IllegalStateException("Cognito 응답에 sub가 없다: storeId=" + storeId));
        log.info("점주 Cognito 계정 생성 storeId={} sub={}", storeId, sub);
        return sub;
    }

    @Override
    public void delete(String email) {
        requirePool();
        client.adminDeleteUser(AdminDeleteUserRequest.builder().userPoolId(userPoolId).username(email).build());
    }

    private void safeDelete(String email) {
        try {
            delete(email);
        } catch (RuntimeException e) {
            log.error("점주 Cognito 계정 정리 실패 (수동 정리 필요) username={}", email, e);
        }
    }

    private void requirePool() {
        if (userPoolId == null || userPoolId.isBlank()) {
            throw new IllegalStateException("COGNITO_USER_POOL_ID가 설정되지 않았다");
        }
    }

    private static AttributeType attr(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }
}
