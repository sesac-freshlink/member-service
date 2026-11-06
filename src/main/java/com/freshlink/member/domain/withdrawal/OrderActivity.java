package com.freshlink.member.domain.withdrawal;

/** order-service에 고객의 진행 중 주문 수를 묻는다 (order 추가 명세 A14, OR-12). 묻지 못하면 예외 */
public interface OrderActivity {

    long activeOrders(String customerId);
}
