package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;

public record PreparedOrderItem(Product product, int quantity) {
}
