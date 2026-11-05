package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.rbdip.bookstore.order.CreateOrderRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.target=4")
class CustomerNameMigrationLoadTest {

    private static final int CLIENTS = 4;
    private static final int TIMEOUT_SECONDS = 30;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void createsAndReadsOrdersWhileContractIsApplied() throws Exception {
        JsonNode product = rest.postForEntity("/products",
                Map.of("name", "Refactoring", "price", "40.00"), JsonNode.class).getBody();
        long productId = product.get("id").asLong();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(CLIENTS);
        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        List<Future<Integer>> clients = new ArrayList<>();
        try {
            startClients(pool, clients, ready, running, productId);
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .load().migrate();

            running.set(false);
            for (Future<Integer> client : clients) {
                assertThat(client.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(2);
            }
        } finally {
            running.set(false);
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void startClients(ExecutorService pool, List<Future<Integer>> clients, CountDownLatch ready,
            AtomicBoolean running, long productId) {
        for (int i = 0; i < CLIENTS; i++) {
            String name = "Customer " + i;
            clients.add(pool.submit(() -> {
                int requests = 0;
                try {
                    roundTrip(name, productId);
                    requests++;
                } finally {
                    ready.countDown();
                }
                while (running.get()) {
                    roundTrip(name, productId);
                    requests++;
                }
                roundTrip(name, productId);
                return requests + 1;
            }));
        }
    }

    private void roundTrip(String name, long productId) {
        CreateOrderRequest request = new CreateOrderRequest(name, "Test address", null, null, null,
                List.of(new CreateOrderRequest.Item(productId, 1)));
        var created = rest.postForEntity("/orders", request, JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long orderId = created.getBody().get("id").asLong();
        var response = rest.getForEntity("/orders", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> names = new ArrayList<>();
        for (JsonNode order : response.getBody()) {
            if (order.get("id").asLong() == orderId) {
                names.add(order.get("customerFullName").asText());
            }
        }
        assertThat(names).containsExactly(name);
    }
}
