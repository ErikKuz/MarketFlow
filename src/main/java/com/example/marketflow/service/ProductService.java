package com.example.marketflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.exception.ProductNotFoundException;
import com.example.marketflow.exception.ProductUnavailableException;
import com.example.marketflow.products.ProductDto;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.products.ProductMapper;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProductService {
    private final ProductRepository rep;

    @Transactional(readOnly = true)
    public List<ProductDto> getAvailableProducts() {
        return rep.findAllByActiveTrueAndQuantityGreaterThan(0)
                .stream()
                .map(ProductMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        ProductEntity product = rep.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (!Boolean.TRUE.equals(product.getActive())
                || product.getQuantity() == null
                || product.getQuantity() <= 0) {
            throw new ProductUnavailableException();
        }

        return ProductMapper.toDto(product);
    }
}
