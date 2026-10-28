package com.freshlink.member.common.config;

import com.freshlink.member.domain.auth.CognitoOwnerAccounts;
import com.freshlink.member.domain.auth.OwnerAccounts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Cognito 관리자 클라이언트. 자격은 기본 체인(파드에서는 IRSA 웹 아이덴티티 토큰)으로 받는다.
 * User Pool ID는 COGNITO_USER_POOL_ID(인프라가 클러스터 공통 환경 변수로 넣는다)
 */
@Configuration
public class CognitoConfig {

    @Bean(destroyMethod = "close")
    public CognitoIdentityProviderClient cognitoIdentityProviderClient(@Value("${app.cognito.region}") String region) {
        return CognitoIdentityProviderClient.builder().region(Region.of(region)).build();
    }

    @Bean
    public OwnerAccounts ownerAccounts(CognitoIdentityProviderClient client,
                                       @Value("${app.cognito.user-pool-id}") String userPoolId) {
        return new CognitoOwnerAccounts(client, userPoolId);
    }
}
