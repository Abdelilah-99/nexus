package com.buy01.order_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.buy01.order_service.client.*;
import com.buy01.order_service.dtos.common.ShippingAddressDto;
import com.buy01.order_service.dtos.external.ProductDetails;
import com.buy01.order_service.dtos.req.*;
import com.buy01.order_service.dtos.response.OrderResponse;
import com.buy01.order_service.exception.OrderRejectedException;
import com.buy01.order_service.repository.*;
import com.buy01.order_service.security.AuthenticatedUser;

/** Opt in with CHECKOUT_TEST_MONGO_URI pointing to a disposable MongoDB database. */
@EnabledIfEnvironmentVariable(named = "CHECKOUT_TEST_MONGO_URI", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.mongodb.uri=${CHECKOUT_TEST_MONGO_URI}", "eureka.client.enabled=false"})
class CheckoutIntegrationTest {
    @Autowired CartService carts;
    @Autowired CartRepository cartRepository;
    @Autowired OrderRepository orders;
    @MockitoBean ProductClient products;
    AuthenticatedUser user;
    ShippingAddressDto address = new ShippingAddressDto("Jane", "0600000000", "1 Main Street", "Casablanca", "20000", "Morocco");

    @TestConfiguration
    static class LocalOrders {
        @Bean @Primary
        OrdersClient localOrders(WebServerApplicationContext context) {
            return new OrdersClient("http://127.0.0.1") {
                @Override public OrderResponse create(CreateOrderRequest request, String key, AuthenticatedUser user) {
                    return new OrdersClient("http://127.0.0.1:" + context.getWebServer().getPort() + "/orders")
                        .create(request, key, user);
                }
            };
        }
    }
    @BeforeEach void setup() {
        user = new AuthenticatedUser("checkout-test-" + UUID.randomUUID(), List.of("CLIENT"));
        when(products.get("p1")).thenReturn(new ProductClient.Product("p1", "Product", new BigDecimal("10.25"), 10, List.of()));
        when(products.getProduct("p1")).thenReturn(new ProductDetails("p1", "Product", 10.25, "seller", List.of(), 10, "test"));
        carts.setItem("p1", 2, true, user);
    }
    @Test void confirmedOrderClearsPersistedCartAndReplayCreatesNoDuplicate() {
        var request = new CheckoutRequest(address, carts.review(user).reviewToken());
        var order = carts.checkout(request, "key", user);
        assertThat(order.totalAmount()).isEqualByComparingTo("20.50");
        assertThat(cartRepository.findById(user.userId()).orElseThrow().getItems()).isEmpty();
        assertThat(orders.findByCustomerIdAndIdempotencyKey(user.userId(), "key")).isPresent();
        assertThat(carts.checkout(request, "key", user).id()).isEqualTo(order.id());
    }
    @Test void priceRaceCreatesNoOrderAndUnlocksPersistedCart() {
        when(products.getProduct("p1")).thenReturn(new ProductDetails("p1", "Product", 11.00, "seller", List.of(), 10, "test"));
        assertRejectedAndEditable();
    }
    @Test void stockRaceCreatesNoOrderAndUnlocksPersistedCart() {
        when(products.getProduct("p1")).thenReturn(new ProductDetails("p1", "Product", 10.25, "seller", List.of(), 1, "test"));
        assertRejectedAndEditable();
    }
    private void assertRejectedAndEditable() {
        var request = new CheckoutRequest(address, carts.review(user).reviewToken());
        assertThatThrownBy(() -> carts.checkout(request, "key", user)).isInstanceOf(OrderRejectedException.class);
        var cart = cartRepository.findById(user.userId()).orElseThrow();
        assertThat(cart.getPendingKey()).isNull();
        assertThat(cart.getItems()).containsEntry("p1", 2);
        assertThat(orders.findByCustomerIdAndIdempotencyKey(user.userId(), "key")).isEmpty();
        assertThat(carts.setItem("p1", 1, false, user).items().get(0).quantity()).isEqualTo(1);
    }
}
