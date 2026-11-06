package com.freshlink.member.domain.auth;

import com.freshlink.member.domain.withdrawal.CustomerAccounts;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

/**
 * 탈퇴 고객의 Cognito 계정을 지운다. ListUsers(sub)로 사용자 이름을 찾고 AdminDeleteUser.
 * 구글 가입자(EXTERNAL_PROVIDER)도 같다. 권한은 member IRSA Role(ListUsers·AdminDeleteUser, 이 User Pool만, 인프라에 이미 있음)
 */
public class CognitoCustomerAccounts implements CustomerAccounts {

    private static final Logger log = LoggerFactory.getLogger(CognitoCustomerAccounts.class);

    private final CognitoIdentityProviderClient client;
    private final String userPoolId;

    public CognitoCustomerAccounts(CognitoIdentityProviderClient client, String userPoolId) {
        this.client = client;
        this.userPoolId = userPoolId;
    }

    @Override
    public Optional<String> delete(String sub) {
        if (userPoolId == null || userPoolId.isBlank()) {
            // PC 단독 실행(Cognito 없음)에서는 지울 계정이 없다. 클러스터에는 인프라가 값을 넣는다
            log.warn("COGNITO_USER_POOL_ID가 없어 Cognito 계정 삭제를 건너뛴다 sub={}", sub);
            return Optional.empty();
        }
        Optional<UserType> user = client.listUsers(ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .filter("sub = \"" + sub.replace("\"", "") + "\"")
                .limit(1)
                .build()).users().stream().findFirst();
        if (user.isEmpty()) {
            return Optional.empty();
        }
        String email = user.get().attributes().stream().filter(a -> "email".equals(a.name()))
                .map(AttributeType::value).findFirst().orElse(null);
        try {
            client.adminDeleteUser(AdminDeleteUserRequest.builder().userPoolId(userPoolId)
                    .username(user.get().username()).build());
        } catch (UserNotFoundException e) {
            // 찾은 뒤 지우기 전에 이미 없어졌다. 지운 것과 같다
        }
        return Optional.ofNullable(email);
    }
}
