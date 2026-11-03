package com.freshlink.member.domain.auth;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

/**
 * Cognito ListUsers로 구글 계정과 같은 이메일의 이메일 가입 계정을 찾는다. 권한은 member IRSA Role (ListUsers, 이 User Pool만).
 * 구글로 들어온 사용자는 상태가 EXTERNAL_PROVIDER다. 이메일 가입자는 그 밖의 상태(CONFIRMED, UNCONFIRMED 등)다
 */
public class CognitoSocialAccounts implements SocialAccounts {

    private static final Logger log = LoggerFactory.getLogger(CognitoSocialAccounts.class);

    private final CognitoIdentityProviderClient client;
    private final String userPoolId;

    public CognitoSocialAccounts(CognitoIdentityProviderClient client, String userPoolId) {
        this.client = client;
        this.userPoolId = userPoolId;
    }

    @Override
    public boolean emailSignupExistsFor(String sub) {
        if (userPoolId == null || userPoolId.isBlank()) {
            // PC 단독 실행(Cognito 없음)에서는 구글 계정이 없으므로 확인하지 않는다. 클러스터에는 인프라가 값을 넣는다
            log.warn("COGNITO_USER_POOL_ID가 없어 구글 계정 이메일 중복 확인을 건너뛴다");
            return false;
        }
        Optional<UserType> me = find("sub", sub, 1).stream().findFirst();
        if (me.isEmpty() || me.get().userStatus() != UserStatusType.EXTERNAL_PROVIDER) {
            return false;
        }
        String email = attribute(me.get(), "email");
        if (email == null || email.isBlank()) {
            return false;
        }
        boolean exists = find("email", email, 60).stream()
                .anyMatch(u -> u.userStatus() != UserStatusType.EXTERNAL_PROVIDER && !u.username().equals(me.get().username()));
        if (exists) {
            log.info("구글 로그인 계정과 같은 이메일의 가입 계정이 있어 프로필 등록을 막는다 sub={}", sub);
        }
        return exists;
    }

    private List<UserType> find(String attribute, String value, int limit) {
        return client.listUsers(ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .filter(attribute + " = \"" + value.replace("\"", "") + "\"")
                .limit(limit)
                .build()).users();
    }

    private static String attribute(UserType user, String name) {
        return user.attributes().stream().filter(a -> name.equals(a.name())).map(AttributeType::value).findFirst().orElse(null);
    }
}
