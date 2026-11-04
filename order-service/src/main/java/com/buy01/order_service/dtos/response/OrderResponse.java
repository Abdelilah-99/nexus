package com.buy01.order_service.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.buy01.order_service.dtos.common.ShippingAddressDto;
import com.buy01.order_service.models.OrderStatus;
import com.buy01.order_service.models.PaymentMethod;
import com.buy01.order_service.models.SellerFulfillment;

public record OrderResponse(
        String id,
        String customerId,
        OrderStatus status,
        List<OrderItemResponse> items,
        ShippingAddressDto shippingAddress,
        PaymentMethod paymentMethod,
        int totalItems,
        BigDecimal totalAmount,
        List<StatusChangeResponse> statusHistory,
        Instant createdAt,
        Instant updatedAt,
        List<SellerFulfillment> fulfillments,
        Boolean cancellable,
        Boolean archived,
        Boolean archivable) {

    public OrderResponse {
        items = items == null ? null : List.copyOf(items);
        statusHistory = statusHistory == null ? null : List.copyOf(statusHistory);
        fulfillments = fulfillments == null ? List.of() : List.copyOf(fulfillments);
        cancellable = Boolean.TRUE.equals(cancellable);
        archived = Boolean.TRUE.equals(archived);
        archivable = Boolean.TRUE.equals(archivable);
    }
}
