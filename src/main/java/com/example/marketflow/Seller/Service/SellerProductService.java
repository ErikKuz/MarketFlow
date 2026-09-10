package com.example.marketflow.Seller.Service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.exception.InvalidQuantityException;
import com.example.marketflow.exception.ProductNotFoundException;
import com.example.marketflow.exception.ProductUnavailableException;
import com.example.marketflow.exception.SellerAccessDeniedException;
import com.example.marketflow.products.CreateProductRequest;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.products.ProductMapper;
import com.example.marketflow.products.SellerProductDto;
import com.example.marketflow.products.UpdateProductRequest;
import com.example.marketflow.service.AuthService;

import lombok.AllArgsConstructor;


@Service
@AllArgsConstructor
public class SellerProductService {
    private final ProductRepository repository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public SellerProductDto getProductById(Long productId, Long sellerId){
        requireSeller(sellerId);

        return repository.findByIdAndSellerId(productId, sellerId)
                .map(ProductMapper::toSellerDto)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public Long createProduct(CreateProductRequest request, Long sellerId){
        
        requireSeller(sellerId);

        return repository.save(ProductMapper.toEntity(request, sellerId)).getId();
    }

    

    @Transactional
    public void updateProduct(Long productId,Long SellerId,UpdateProductRequest dto){

        requireSeller(SellerId);

        ProductEntity entity=repository.findForSellerUpdate(productId, SellerId).orElseThrow(() -> new ProductNotFoundException(productId));
        if(dto.getName()!=null)entity.setName(dto.getName());
        if(dto.getPrice()!=null)entity.setPrice(dto.getPrice());
        if(dto.getDescription()!=null)entity.setDescription(dto.getDescription());
        if(dto.getImageUrl()!=null)entity.setUrl(dto.getImageUrl());
    }

    @Transactional
    public void DisableProduct(Long ProductId,Long SellerId){

        requireSeller(SellerId);

        ProductEntity entity=repository.findForSellerUpdate(ProductId,SellerId).
        orElseThrow(()->new ProductNotFoundException(ProductId));
        entity.setActive(false);
    }

    @Transactional
    public void EnableProduct(Long ProductId,Long SellerId){
        
        requireSeller(SellerId);

        ProductEntity entity=repository.findForSellerUpdate(ProductId,SellerId)
        .orElseThrow(()->new ProductNotFoundException(ProductId));
        if (entity.getQuantity() == null || entity.getQuantity() <= 0) {
            throw new ProductUnavailableException();
        }

        entity.setActive(true);
    }

    @Transactional
    public void RestokeQuanityProduct(Long productId,Long SellerId,Integer amount){

        requireSeller(SellerId);

        if (amount == null || amount <= 0) {
            throw new InvalidQuantityException(amount);
        }

        ProductEntity entity=repository.findForSellerUpdate(productId, SellerId).orElseThrow(
            ()->new ProductNotFoundException(productId)
        );
        
        Integer currentQuantity = entity.getQuantity();

        if (currentQuantity == null) {
            currentQuantity = 0;
        }

        entity.setQuantity(currentQuantity + amount);
    }

    private void requireSeller(Long sellerId) {
        if (!Boolean.TRUE.equals(authService.isSeller(sellerId))) {
            throw new SellerAccessDeniedException();
        }
    }
}
