package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final String DEFAULT_CUSTOMER_TYPE = "regular";

    private final OrderValidator orderValidator;
    private final OrderItemPreparer orderItemPreparer;
    private final PricingCalculator pricingCalculator;
    private final OrderWriter orderWriter;
    private final OrderConfirmationSender confirmationSender;

    public OrderService(
            OrderValidator orderValidator,
            OrderItemPreparer orderItemPreparer,
            PricingCalculator pricingCalculator,
            OrderWriter orderWriter,
            OrderConfirmationSender confirmationSender) {
        this.orderValidator = orderValidator;
        this.orderItemPreparer = orderItemPreparer;
        this.pricingCalculator = pricingCalculator;
        this.orderWriter = orderWriter;
        this.confirmationSender = confirmationSender;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        orderValidator.validate(request);
        List<PreparedOrderItem> items = orderItemPreparer.prepare(request.items());
        List<PricingCalculator.LineItem> lineItems = items.stream()
                .map(item -> new PricingCalculator.LineItem(item.product().getPrice(), item.quantity()))
                .toList();
        String customerType = request.customerType() == null ? DEFAULT_CUSTOMER_TYPE : request.customerType();
        BigDecimal total = pricingCalculator.calculateOrderTotal(lineItems, customerType, request.couponCode());

        Order order = orderWriter.save(request, items);
        confirmationSender.send(request.customerFullName(), order.getId(), total);
        return order;
    }
}
