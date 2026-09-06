package com.example.marketflow.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Order.OrderDetailsDto;
import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderItemEntity;
import com.example.marketflow.Order.OrderMapper;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.CartItemRepository;
import com.example.marketflow.Repository.OrderItemRepository;
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.cart.CartItemEntity;
import com.example.marketflow.exception.InsufficientStockException;
import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.exception.InvalidQuantityException;
import com.example.marketflow.exception.NoSelectedCartItemsException;
import com.example.marketflow.exception.NotEnoughProductQuantityException;
import com.example.marketflow.exception.OrderNotFoundException;
import com.example.marketflow.exception.OrderCancellationFailedException;
import com.example.marketflow.exception.ProductNotFoundException;
import com.example.marketflow.exception.ProductUnavailableException;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.payment.PaymentStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class  OrderService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentService paymentService;
    private final com.example.marketflow.marketplace.OrderWorkflowService workflow;

    @Transactional
    public Long createOrder(Long buyerId) {
        List<CartItemEntity> selectedItems =
                cartItemRepository.findAllByBuyerIdAndSelectedTrue(buyerId);

        if (selectedItems.isEmpty()) {
            throw new NoSelectedCartItemsException();
        }

        List<Long> productIds = selectedItems.stream()
                .map(CartItemEntity::getProductId)
                .distinct()
                .toList();

        List<ProductEntity> products =
                productRepository.findAllById(productIds);

        Map<Long, ProductEntity> productsById = products.stream()
                .collect(Collectors.toMap(
                        ProductEntity::getId,
                        Function.identity()
                ));

        BigDecimal amount = BigDecimal.ZERO;

        for (CartItemEntity cartItem : selectedItems) {
            if (cartItem.getQuantity() == null
                    || cartItem.getQuantity() <= 0) {
                throw new InvalidQuantityException(cartItem.getQuantity());
            }

            ProductEntity product =
                    productsById.get(cartItem.getProductId());

            if (product == null) {
                throw new ProductNotFoundException(cartItem.getProductId());
            }

            if (!Boolean.TRUE.equals(product.getActive()) || product.isHidden()) {
                throw new ProductUnavailableException();
            }

            if (product.getQuantity() == null
                    || product.getQuantity() < cartItem.getQuantity()) {
                throw new NotEnoughProductQuantityException();
            }

            BigDecimal itemTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            amount = amount.add(itemTotal);
        }

        List<CartItemEntity> itemsForStockUpdate = selectedItems.stream()
                .sorted(Comparator.comparing(CartItemEntity::getProductId))
                .toList();

        for (CartItemEntity cartItem : itemsForStockUpdate) {
            int updatedRows = productRepository.decreaseStock(
                    cartItem.getProductId(),
                    cartItem.getQuantity()
            );

            if (updatedRows != 1) {
                throw new InsufficientStockException();
            }
        }

        OrderEntity savedOrder = orderRepository.save(
                new OrderEntity(
                        buyerId,
                        OrderStatus.CREATED,
                        amount
                )
        );

        List<OrderItemEntity> orderItems = new ArrayList<>();

        for (CartItemEntity cartItem : selectedItems) {
            ProductEntity product =
                    productsById.get(cartItem.getProductId());

            orderItems.add(
                    new OrderItemEntity(
                            savedOrder.getId(),
                            product.getId(),
                            product.getSellerId(),
                            product.getName(),
                            product.getPrice(),
                            cartItem.getQuantity(),
                            product.getUrl()
                    )
            );
        }

        orderItemRepository.saveAll(orderItems);
        workflow.initialize(savedOrder, orderItems);
        cartItemRepository.deleteSelectedByBuyerId(buyerId);

        return savedOrder.getId();
    }

    @Transactional(readOnly = true)
    public OrderDetailsDto getOrderDetails(Long orderId, Long buyerId) {
        OrderEntity order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        List<OrderItemEntity> orderItems =
                orderItemRepository.findAllByOrderId(orderId);

        return OrderMapper.toDetailsDto(order, orderItems);
    }

    @Transactional
    public void cancelOrder(Long orderId, Long buyerId) {
        cancelOrder(orderId, buyerId, buyerId, "BUYER_CANCELLED");
    }

    @Transactional
    public void cancelOrder(Long orderId, Long buyerId, Long actorId, String reason) {
        OrderEntity order = orderRepository.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        if (order.getPaymentStatus() == PaymentStatus.PROCESSING) {
            throw new InvalidOrderStateException(
                    "An order cannot be cancelled while payment is processing"
            );
        }

        boolean unpaidOrder = order.getStatus() == OrderStatus.CREATED
                && (order.getPaymentStatus() == PaymentStatus.NOT_PAID
                        || order.getPaymentStatus() == PaymentStatus.FAILED);

        boolean paidOrder = order.getStatus() == OrderStatus.CONFIRMED
                && order.getPaymentStatus() == PaymentStatus.PAID;

        if (!unpaidOrder && !paidOrder) {
            throw new InvalidOrderStateException(
                    "Order cannot be cancelled in status "
                            + order.getStatus()
                            + " with payment status "
                            + order.getPaymentStatus()
            );
        }

        if (paidOrder) {
            paymentService.refundOrder(order);
        }

        List<OrderItemEntity> orderItems = orderItemRepository
                .findAllByOrderId(order.getId())
                .stream()
                .sorted(Comparator.comparing(OrderItemEntity::getProductId))
                .toList();

        for (OrderItemEntity orderItem : orderItems) {
            int updatedRows = productRepository.increaseStock(
                    orderItem.getProductId(),
                    orderItem.getQuantity()
            );

            if (updatedRows != 1) {
                throw new OrderCancellationFailedException(
                        orderItem.getProductId()
                );
            }
        }

        workflow.cancelled(order, actorId, reason);
        order.changeStatus(OrderStatus.CANCELLED);
    }
}
