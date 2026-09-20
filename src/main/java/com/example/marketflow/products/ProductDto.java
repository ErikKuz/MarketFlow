package com.example.marketflow.products;

import java.io.Serializable;
import java.math.BigDecimal;

public record ProductDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer quantity,
        String imageUrl
) implements Serializable {
}
