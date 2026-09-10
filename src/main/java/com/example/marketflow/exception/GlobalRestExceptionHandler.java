package com.example.marketflow.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;




@RestControllerAdvice(
        basePackages = "com.example.marketflow.RestController"
)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalRestExceptionHandler {
    @ExceptionHandler(com.example.marketflow.marketplace.MarketplaceException.class)
    public ResponseEntity<ApiError> handleMarketplace(
            com.example.marketflow.marketplace.MarketplaceException e, HttpServletRequest request) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(Instant.now(), e.getStatus().value(),
                e.getCode(), e.getMessage(), request.getRequestURI(), List.of(), null));
    }
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFoundException(ProductNotFoundException exception,
        HttpServletRequest request){
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "PRODUCT_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ResponseEntity<ApiError> handleProductUnavailableException(ProductUnavailableException exception,HttpServletRequest request){
        ApiError error=new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "PRODUCT_UNAVAILABLE",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );
        return ResponseEntity.status(409).body(error);
    }

    @ExceptionHandler(InvalidProductId.class)
    public ResponseEntity<ApiError> handleInvalidProductId(
            InvalidProductId exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PRODUCT_ID",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(
                        new ValidationError(
                                "productId",
                                "must be greater than or equal to 1"
                        )
                ),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(InvalidCartItemIdException.class)
    public ResponseEntity<ApiError> handleInvalidCartItemId(
            InvalidCartItemIdException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_CART_ITEM_ID",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(
                        new ValidationError(
                                "itemId",
                                "must be greater than or equal to 1"
                        )
                ),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiError> handleAuthenticationRequired(
            AuthenticationRequiredException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "AUTHENTICATION_REQUIRED",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(error);
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ApiError> handleCartItemNotFound(
            CartItemNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "CART_ITEM_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(InvalidQuantityException.class)
    public ResponseEntity<ApiError> handleInvalidQuantity(
            InvalidQuantityException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_QUANTITY",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(
                        new ValidationError(
                                "quantity",
                                "must be greater than or equal to 1"
                        )
                ),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(
            InsufficientStockException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "INSUFFICIENT_STOCK",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String field = exception.getName();
        String code;
        String message;

        if ("itemId".equals(field)) {
            code = "INVALID_CART_ITEM_ID";
            message = "Cart item ID must be a positive integer";
        } else if ("orderId".equals(field)) {
            code = "INVALID_ORDER_ID";
            message = "Order ID must be a positive integer";
        } else {
            code = "INVALID_PRODUCT_ID";
            message = "Product ID must be a positive integer";
        }

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                code,
                message,
                request.getRequestURI(),
                List.of(
                        new ValidationError(
                                field,
                                "must be a positive integer"
                        )
                ),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        var fieldError = exception.getBindingResult().getFieldError();
        String field = fieldError == null ? "request" : fieldError.getField();
        String validationMessage = fieldError == null
                ? "invalid value"
                : fieldError.getDefaultMessage();

        String code;
        String message;

        if ("productId".equals(field)) {
            code = "INVALID_PRODUCT_ID";
            message = "Product ID must be a positive integer";
        } else if ("quantity".equals(field)) {
            code = "INVALID_QUANTITY";
            message = "Quantity must be greater than or equal to 1";
        } else if ("amount".equals(field)) {
            code = "INVALID_AMOUNT";
            message = "Amount must be greater than zero";
        } else if ("selected".equals(field)) {
            code = "INVALID_SELECTION";
            message = "Selected value is required";
        } else {
            code = "VALIDATION_ERROR";
            message = "Request validation failed";
        }

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                code,
                message,
                request.getRequestURI(),
                List.of(new ValidationError(field, validationMessage)),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "EMAIL_ALREADY_EXISTS",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "INVALID_CREDENTIALS",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(
            UserNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "USER_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(SellerAccessDeniedException.class)
    public ResponseEntity<ApiError> handleSellerAccessDenied(
            SellerAccessDeniedException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "SELLER_ACCESS_DENIED",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(NoSelectedCartItemsException.class)
    public ResponseEntity<ApiError> handleNoSelectedCartItems(
            NoSelectedCartItemsException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "NO_SELECTED_CART_ITEMS",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidOrderIdException.class)
    public ResponseEntity<ApiError> handleInvalidOrderId(
            InvalidOrderIdException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_ORDER_ID",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(new ValidationError(
                        "orderId",
                        "must be greater than or equal to 1"
                )),
                null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "ORDER_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(NotEnoughProductQuantityException.class)
    public ResponseEntity<ApiError> handleNotEnoughProductQuantity(
            NotEnoughProductQuantityException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "INSUFFICIENT_STOCK",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PaymentCardNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentCardNotFound(
            PaymentCardNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "PAYMENT_CARD_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(
            InsufficientFundsException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "INSUFFICIENT_FUNDS",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PaymentAlreadyProcessedException.class)
    public ResponseEntity<ApiError> handlePaymentAlreadyProcessed(
            PaymentAlreadyProcessedException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "PAYMENT_ALREADY_PROCESSED",
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiError> handleInvalidOrderState(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        String code = "INVALID_ORDER_STATE";

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                code,
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                Instant.now(), 409, "DATA_CONFLICT",
                "Request conflicts with existing data; check the order before retrying",
                request.getRequestURI(), List.of(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_REQUEST",
                "Request body contains invalid JSON or unsupported values",
                request.getRequestURI(),
                List.of(),
                null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

}
