package com.buy01.order_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.buy01.order_service.models.Order;
import com.buy01.order_service.models.OrderStatus;
import com.buy01.order_service.models.SellerFulfillment;

class OrderProgressTest {
    @Test
    void differentActiveStatusesRemainInProgressAndFinalMixedPartsAreCompleted() {
        assertThat(OrderProgress.overall(parts(OrderStatus.DELIVERED, OrderStatus.PENDING)))
                .isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(OrderProgress.overall(parts(OrderStatus.DELIVERED, OrderStatus.CANCELLED)))
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(OrderProgress.overall(parts(OrderStatus.DELIVERED, OrderStatus.DELIVERED)))
                .isEqualTo(OrderStatus.DELIVERED);
        assertThat(OrderProgress.overall(parts(OrderStatus.CANCELLED, OrderStatus.CANCELLED)))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellationRequiresAllRemainingPartsToBeUnshipped() {
        assertThat(OrderProgress.cancellable(parts(OrderStatus.PENDING, OrderStatus.CONFIRMED))).isTrue();
        assertThat(OrderProgress.cancellable(parts(OrderStatus.CANCELLED, OrderStatus.PENDING))).isTrue();
        assertThat(OrderProgress.cancellable(parts(OrderStatus.CANCELLED, OrderStatus.CANCELLED))).isFalse();
        assertThat(OrderProgress.cancellable(parts(OrderStatus.PENDING, OrderStatus.SHIPPED))).isFalse();
        assertThat(OrderProgress.cancellable(parts(OrderStatus.DELIVERED, OrderStatus.PENDING))).isFalse();
    }

    @Test
    void legacyOrdersUseHistoricalStatusWithoutChangingStoredData() {
        var order = Order.builder().sellerIds(List.of("a", "b")).status(OrderStatus.CONFIRMED).build();
        assertThat(OrderProgress.parts(order)).extracting(SellerFulfillment::status)
                .containsExactly(OrderStatus.CONFIRMED, OrderStatus.CONFIRMED);
        assertThat(order.getFulfillments()).isNull();
        order.setFulfillments(List.of());
        assertThat(OrderProgress.parts(order)).hasSize(2);
        order.setFulfillments(parts(OrderStatus.SHIPPED, OrderStatus.PENDING));
        assertThat(OrderProgress.parts(order)).isEqualTo(order.getFulfillments());
    }

    private List<SellerFulfillment> parts(OrderStatus first, OrderStatus second) {
        return List.of(new SellerFulfillment("a", first, List.of()), new SellerFulfillment("b", second, List.of()));
    }
}
