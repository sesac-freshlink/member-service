package com.freshlink.member.domain.auth;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
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
            // 값이 빠진 채 건너뛰면 계정은 살아 있는데 프로필만 지워진다. 탈퇴를 실패(502)로 돌린다
            log.error("COGNITO_USER_POOL_ID가 없어 Cognito 계정을 지울 수 없다 sub={}", sub);
            throw new BusinessException(MemberErrorCode.WITHDRAWAL_FAILED);
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
