package com.example.marketflow.Seller.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.exception.ProductUnavailableException;
import com.example.marketflow.exception.SellerAccessDeniedException;
import com.example.marketflow.products.CreateProductRequest;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.products.UpdateProductRequest;
import com.example.marketflow.service.AuthService;

@ExtendWith(MockitoExtension.class)
class SellerProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private SellerProductService sellerProductService;

    @Test
    void createProductChecksSellerAndSavesMappedProduct() {
        CreateProductRequest request = new CreateProductRequest(
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("1000.00"),
                10,
                "keyboard.jpg"
        );
        ProductEntity saved = new ProductEntity(
                7L,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("1000.00"),
                10,
                "keyboard.jpg"
        );
        saved.setId(20L);

        when(authService.isSeller(7L)).thenReturn(true);
        when(productRepository.save(any(ProductEntity.class))).thenReturn(saved);

        Long result = sellerProductService.createProduct(request, 7L);

        assertEquals(20L, result);
        ArgumentCaptor<ProductEntity> captor =
                ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getSellerId());
        assertEquals("Keyboard", captor.getValue().getName());
        assertEquals(10, captor.getValue().getQuantity());
    }

    @Test
    void createProductRejectsUserWithoutSellerRoleBeforeRepositoryCall() {
        when(authService.isSeller(7L)).thenReturn(false);

        assertThrows(
                SellerAccessDeniedException.class,
                () -> sellerProductService.createProduct(
                        new CreateProductRequest(
                                "Keyboard",
                                null,
                                new BigDecimal("1000.00"),
                                10,
                                null
                        ),
                        7L
                )
        );

        verifyNoInteractions(productRepository);
    }

    @Test
    void enableProductRejectsProductWithoutStock() {
        ProductEntity product = product();
        product.setActive(false);
        product.setQuantity(0);
        when(authService.isSeller(7L)).thenReturn(true);
        when(productRepository.findForSellerUpdate(20L, 7L))
                .thenReturn(Optional.of(product));

        assertThrows(
                ProductUnavailableException.class,
                () -> sellerProductService.EnableProduct(20L, 7L)
        );

        assertFalse(product.getActive());
    }

    @Test
    void updateProductChangesOnlyProvidedFields() {
        ProductEntity product = product();
        BigDecimal originalPrice = product.getPrice();
        when(authService.isSeller(7L)).thenReturn(true);
        when(productRepository.findForSellerUpdate(20L, 7L))
                .thenReturn(Optional.of(product));

        sellerProductService.updateProduct(
                20L,
                7L,
                new UpdateProductRequest("Updated keyboard", null, null, null)
        );

        assertEquals("Updated keyboard", product.getName());
        assertEquals(originalPrice, product.getPrice());
        assertEquals("Mechanical keyboard", product.getDescription());
    }

    private ProductEntity product() {
        ProductEntity product = new ProductEntity(
                7L,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("1000.00"),
                10,
                "keyboard.jpg"
        );
        product.setId(20L);
        return product;
    }
}
