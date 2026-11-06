package com.freshlink.member.domain.withdrawal;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * order-service GET /internal/customers/{id}/active-orders 를 부른다. 클러스터 안 Service 이름(http://order-service:8080)으로,
 * gateway를 거치지 않는다. 시간 안에 답이 없으면 예외(탈퇴하지 않는다). 백엔드 공통 규칙 7장(타임아웃을 둔 동기 HTTP)
 */
@Component
public class HttpOrderActivity implements OrderActivity {

    private final RestClient client;

    public HttpOrderActivity(@Value("${app.order.base-url}") String baseUrl,
                             @Value("${app.order.timeout:2s}") Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public long activeOrders(String customerId) {
        Response res = client.get().uri("/internal/customers/{id}/active-orders", customerId)
                .retrieve().body(Response.class);
        if (res == null) {
            throw new IllegalStateException("order 응답 본문이 없다");
        }
        return res.activeOrders();
    }

    record Response(long activeOrders) {
    }
}
