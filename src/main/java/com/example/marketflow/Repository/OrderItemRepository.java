package com.example.marketflow.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.marketflow.Order.OrderItemEntity;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findAllByOrderId(Long orderId);
    List<OrderItemEntity> findAllByOrderIdAndSellerId(Long orderId, Long sellerId);
}
