package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "100.00, 10, regular, , 1000.00",
        "100.00, 11, regular, , 1024.10",
        "100.00, 1, vip, , 90.00",
        "100.00, 1, wholesale, , 85.00",
        "100.00, 1, regular, SAVE10, 90.00",
        "100.00, 1, regular, SAVE20PERCENT, 80.00",
        "5.00, 1, regular, SAVE10, 0.00",
        "100.00, 11, vip, SAVE20PERCENT, 752.40",
        "200.00, 10, wholesale, SAVE10, 1656.20",
        "100.01, 10, regular, , 980.10",
        "0.335, 3, regular, , 1.01",
        "100.00, 1, unknown, UNKNOWN, 100.00",
        "100.00, 1, , , 100.00"
    })
    void preservesDiscountOrderAndRounding(
            BigDecimal price, int quantity, String customerType, String couponCode, BigDecimal expected) {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(price, quantity)), customerType, couponCode);

        assertThat(total).isEqualTo(expected);
    }

    @Test
    void appliesQuantityDiscountToEachLineSeparately() {
        List<PricingCalculator.LineItem> items = List.of(
                new PricingCalculator.LineItem(new BigDecimal("10.00"), 11),
                new PricingCalculator.LineItem(new BigDecimal("20.00"), 2));

        assertThat(calculator.calculateOrderTotal(items, "regular", null))
                .isEqualTo(new BigDecimal("144.50"));
    }
}
