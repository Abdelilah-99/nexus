package com.buy01.order_service.service;

import java.util.List;

import com.buy01.order_service.models.Order;
import com.buy01.order_service.models.OrderStatus;
import com.buy01.order_service.models.SellerFulfillment;

/** Shared interpretation of persisted and pre-fulfillment orders. */
public final class OrderProgress {
    private OrderProgress() { }

    public static List<SellerFulfillment> parts(Order order) {
        if (order.getFulfillments() != null && !order.getFulfillments().isEmpty()) {
            return order.getFulfillments();
        }
        // Legacy records have no delivery evidence beyond the original overall status.
        return order.getSellerIds().stream().map(seller -> new SellerFulfillment(seller,
                order.getStatus(), order.getStatusHistory() == null ? List.of() : order.getStatusHistory())).toList();
    }

    public static OrderStatus overall(List<SellerFulfillment> parts) {
        List<OrderStatus> statuses = parts.stream().map(SellerFulfillment::status).distinct().toList();
        if (statuses.size() == 1) {
            return statuses.get(0);
        }
        return statuses.stream().allMatch(status -> status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED)
                ? OrderStatus.COMPLETED : OrderStatus.IN_PROGRESS;
    }

    public static boolean cancellable(List<SellerFulfillment> parts) {
        return parts.stream().anyMatch(part -> part.status() != OrderStatus.CANCELLED)
                && parts.stream().allMatch(part -> part.status() == OrderStatus.CANCELLED
                        || part.status().canTransitionTo(OrderStatus.CANCELLED));
    }
}
