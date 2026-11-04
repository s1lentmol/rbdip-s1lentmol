package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderCreationTest extends AbstractIntegrationTest {

    @Test
    void keepsProductsAndQuantitiesTogetherAndDefaultsMissingQuantity() {
        long firstProduct = createProduct("Refactoring", "40.00");
        long secondProduct = createProduct("Clean Code", "35.00");
        CreateOrderRequest request = request(List.of(
                new CreateOrderRequest.Item(firstProduct, null),
                new CreateOrderRequest.Item(secondProduct, 2),
                new CreateOrderRequest.Item(firstProduct, 3)));

        ResponseEntity<JsonNode> response = createOrder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("new");
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT p.name AS product_name, p.price AS product_price, i.quantity
                FROM order_items i JOIN products p ON p.id = i.product_id
                ORDER BY i.id
                """))
                .containsExactly(
                        Map.of("product_name", "Refactoring", "product_price", new BigDecimal("40.00"),
                                "quantity", 1),
                        Map.of("product_name", "Clean Code", "product_price", new BigDecimal("35.00"),
                                "quantity", 2),
                        Map.of("product_name", "Refactoring", "product_price", new BigDecimal("40.00"),
                                "quantity", 3));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissingCustomerName(String name) {
        CreateOrderRequest request = new CreateOrderRequest(
                name, "Test address", null, null, null, List.of(new CreateOrderRequest.Item(1L, 1)));

        assertRejected(request, "customerFullName is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsMissingAddress(String address) {
        CreateOrderRequest request = new CreateOrderRequest(
                "Ivan Petrov", address, null, null, null, List.of(new CreateOrderRequest.Item(1L, 1)));

        assertRejected(request, "customerAddress is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidQuantityWithoutSavingPartialOrder(int quantity) {
        long productId = createProduct("Refactoring", "40.00");
        CreateOrderRequest request = request(List.of(
                new CreateOrderRequest.Item(productId, 1),
                new CreateOrderRequest.Item(productId, quantity)));

        assertRejected(request, "quantity must be positive");
    }

    @Test
    void rejectsUnknownProductWithoutSavingPartialOrder() {
        long productId = createProduct("Refactoring", "40.00");
        long missingProductId = productId + 1;
        CreateOrderRequest request = request(List.of(
                new CreateOrderRequest.Item(productId, 1),
                new CreateOrderRequest.Item(missingProductId, 1)));

        assertRejected(request, "product " + missingProductId + " not found");
    }

    @Test
    void repeatedOrdersReuseTheCustomerAndKeepTheSelectedCatalogProduct() {
        long firstProduct = createProduct("Same title", "19.00");
        long secondProduct = createProduct("Same title", "19.00");
        assertThat(createOrder(request(List.of(new CreateOrderRequest.Item(firstProduct, 1)))).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(createOrder(request(List.of(new CreateOrderRequest.Item(secondProduct, 1)))).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT customer_id) FROM orders", Long.class))
                .isOne();
        assertThat(jdbcTemplate.queryForList("SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(firstProduct, secondProduct);
    }

    @Test
    void rejectsMissingItems() {
        assertRejected(request(null), "order must contain at least one item");
    }

    private CreateOrderRequest request(List<CreateOrderRequest.Item> items) {
        return new CreateOrderRequest("Ivan Petrov", "Test address", null, null, null, items);
    }

    private long createProduct(String name, String price) {
        JsonNode body = restTemplate.postForEntity(
                "/products", Map.of("name", name, "price", price), JsonNode.class).getBody();
        return body.get("id").asLong();
    }

    private ResponseEntity<JsonNode> createOrder(CreateOrderRequest request) {
        return restTemplate.postForEntity("/orders", request, JsonNode.class);
    }

    private void assertRejected(CreateOrderRequest request, String message) {
        ResponseEntity<JsonNode> response = createOrder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").asText()).isEqualTo(message);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_items", Long.class)).isZero();
    }
}
