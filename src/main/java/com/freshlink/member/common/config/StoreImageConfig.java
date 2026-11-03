package com.freshlink.member.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 점포 이미지 presigned PUT 주소를 만드는 S3Presigner. 주소는 서버에서 서명만 하고 S3를 부르지 않는다.
 * 자격은 기본 체인(파드에서는 IRSA 웹 아이덴티티 토큰)으로 서명할 때 받는다. 권한은 인프라가 images/stores/ 아래 PutObject만 준다
 */
@Configuration
public class StoreImageConfig {

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(@Value("${app.cognito.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
