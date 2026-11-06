package com.freshlink.member.domain.auth;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 탈퇴 고객 Cognito 계정 삭제. 실제 Cognito에 접속하지 않는다 (SDK 클라이언트 목) */
class CognitoCustomerAccountsTest {

    private static final String SUB = "aaaaaaaa-1111-2222-3333-444444444444";

    CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);

    @Test
    void User_Pool_ID가_없으면_건너뛰지_않고_WITHDRAWAL_FAILED() {
        for (String pool : new String[] {null, " "}) {
            CognitoCustomerAccounts accounts = new CognitoCustomerAccounts(client, pool);
            assertThatThrownBy(() -> accounts.delete(SUB))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(MemberErrorCode.WITHDRAWAL_FAILED));
        }
        verifyNoInteractions(client);
    }
}
