package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.products.*;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class CatalogService {
    private final ProductRepository products;

    @Transactional(readOnly = true)
    public PageView<ProductDto> search(String q, BigDecimal minPrice, BigDecimal maxPrice, Long sellerId,
            String sort, int page, int size) {
        MarketplaceAccess.page(page, size);
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
                || (sellerId != null && sellerId < 1) || (q != null && q.length() > 200))
            throw new MarketplaceException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH", "Invalid catalog filters");
        Sort ordering = switch (sort) {
            case "priceAsc" -> Sort.by("price").ascending();
            case "priceDesc" -> Sort.by("price").descending();
            case "name" -> Sort.by("name").ascending();
            case "newest" -> Sort.by("createdAt").descending();
            default -> throw new MarketplaceException(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Use newest, priceAsc, priceDesc or name");
        };
        Specification<ProductEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.isFalse(root.get("hidden")));
            predicates.add(cb.greaterThan(root.get("quantity"), 0));
            if (q != null && !q.isBlank()) {
                String escaped = q.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '!'));
            }
            if (minPrice != null) predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            if (maxPrice != null) predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            if (sellerId != null) predicates.add(cb.equal(root.get("sellerId"), sellerId));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageView.of(products.findAll(spec, PageRequest.of(page, size, ordering.and(Sort.by("id")))).map(ProductMapper::toDto));
    }
}
