package com.buy01.order_service.models;

import java.util.List;

/** Delivery state for one seller's items; independent of the other sellers. */
public record SellerFulfillment(String sellerId, OrderStatus status, List<StatusChange> statusHistory) {
    public SellerFulfillment {
        statusHistory = List.copyOf(statusHistory);
    }
}
