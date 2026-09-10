package com.example.marketflow.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.exception.ProductNotFoundException;
import com.example.marketflow.exception.ProductUnavailableException;
import com.example.marketflow.products.ProductDto;
import com.example.marketflow.products.ProductEntity;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    ProductRepository productrepository;

    @InjectMocks
    ProductService productservice;

    @Test
    void shouldThrowProductUnavailableWhenProductIsInactive() {
        // Подготовка
        ProductEntity entity = new ProductEntity(
                22L,
                "name",
                "description",
                new BigDecimal("123213213"),
                15,
                "dsadsasda.jpg"
        );

        entity.setId(20L);
        entity.setActive(false);

        when(productrepository.findById(entity.getId()))
                .thenReturn(Optional.of(entity));

        // Выполнение и проверка
        assertThrows(
                ProductUnavailableException.class,
                () -> productservice.getProductById(entity.getId())
        );

        verify(productrepository).findById(entity.getId());
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, -10})
    void shouldThrowProductUnavailableWhenQuantityIsInvalid(Integer quantity) {
        // Подготовка
        Long productId = 20L;

        ProductEntity entity = new ProductEntity(
                22L,
                "name",
                "description",
                new BigDecimal("123213213"),
                quantity,
                "dsadsasda.jpg"
        );

        entity.setId(productId);
        entity.setActive(true);

        when(productrepository.findById(productId))
                .thenReturn(Optional.of(entity));

        // Выполнение и проверка
        assertThrows(
                ProductUnavailableException.class,
                () -> productservice.getProductById(productId)
        );

        verify(productrepository).findById(productId);
    }


    @Test
    void shouldReturnAvailableProducts(){
        ProductEntity product = new ProductEntity(231231L,"kname","descr",
            new BigDecimal("1233"),13,"dsasad.jpg");
        
        product.setId(31L);
        when(productrepository.findAllByActiveTrueAndQuantityGreaterThan(0))
        .thenReturn(List.of(product));
        
        List<ProductDto> list= productservice.getAvailableProducts();

        ProductDto dto=list.get(0);

        assertEquals(31,dto.id());
        assertEquals("kname",dto.name());
        assertEquals("descr",dto.description());
        assertEquals(new BigDecimal(1233),dto.price());
        assertEquals(13,dto.quantity());


        verify(productrepository).findAllByActiveTrueAndQuantityGreaterThan(0);
    }

    @Test
    void shouldReturnEmptyListWhenNoAvailableProductsExist(){
        when(productrepository.findAllByActiveTrueAndQuantityGreaterThan(0)).thenReturn(List.of());

        List<ProductDto> list=productservice.getAvailableProducts();

        assertTrue(list.isEmpty());

        verify(productrepository).findAllByActiveTrueAndQuantityGreaterThan(0);
    }

    @Test
    void shouldReturnProductDtoWhenProductExistsAndIsAvailable() {
        // Подготовка
        Long productId = 20L;

        ProductEntity product = new ProductEntity(
                33L,
                "dsa",
                "decr",
                new BigDecimal("321231"),
                14,
                "sdsasdsda.jpg"
        );

        product.setId(productId);
        product.setActive(true);

        when(productrepository.findById(productId))
                .thenReturn(Optional.of(product));

        // Выполнение
        ProductDto dto = productservice.getProductById(productId);

        // Проверка
        assertEquals(productId, dto.id());
        assertEquals("dsa", dto.name());
        assertEquals("decr", dto.description());
        assertEquals(
                0,
                new BigDecimal("321231").compareTo(dto.price())
        );
        assertEquals(14, dto.quantity());
        assertEquals("sdsasdsda.jpg", dto.imageUrl());
        assertInstanceOf(ProductDto.class, dto);

        verify(productrepository).findById(productId);
    }


    @Test
    void shouldThrowProductNotFoundWhenProductDoesNotExist(){
        Long productId=999L;
        when(productrepository.findById(productId)).thenReturn(Optional.empty());

        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class,()-> productservice.getProductById(productId));
    

        assertEquals(
                "Продукт с таким id:999 не найден",
                exception.getMessage()
        );

        verify(productrepository).findById(productId);
    }
}
