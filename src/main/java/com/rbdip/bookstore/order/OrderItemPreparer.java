package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderItemPreparer {

    private final ProductRepository productRepository;
    private final OrderValidator orderValidator;

    public OrderItemPreparer(ProductRepository productRepository, OrderValidator orderValidator) {
        this.productRepository = productRepository;
        this.orderValidator = orderValidator;
    }

    public List<PreparedOrderItem> prepare(List<CreateOrderRequest.Item> items) {
        return items.stream().map(this::prepareItem).toList();
    }

    private PreparedOrderItem prepareItem(CreateOrderRequest.Item item) {
        Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new IllegalArgumentException("product " + item.productId() + " not found"));
        return new PreparedOrderItem(product, orderValidator.resolveQuantity(item.quantity()));
    }
}
