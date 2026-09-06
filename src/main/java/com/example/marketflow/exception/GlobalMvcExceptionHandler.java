package com.example.marketflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalMvcExceptionHandler {
    @ExceptionHandler(com.example.marketflow.marketplace.MarketplaceException.class)
    public String handleMarketplace(com.example.marketflow.marketplace.MarketplaceException e,
            jakarta.servlet.http.HttpServletResponse response, Model model) {
        response.setStatus(e.getStatus().value());
        model.addAttribute("message", e.getMessage());
        return "error";
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "registerDuplicate";
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(
            InvalidCredentialsException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "loginUnsuccessful";
    }
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUserNotFoundException(UserNotFoundException exception,Model model){
        model.addAttribute("message",exception.getMessage());
        return "error";
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleProductNotFoundException(ProductNotFoundException exception,Model model){
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleCartItemNotFoundException(CartItemNotFoundException exception,Model model){
        model.addAttribute("message", exception.getMessage());
        return "error";
    }
    
    @ExceptionHandler(ProductUnavailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleProductUnavailableException(ProductUnavailableException exception,Model model){
        model.addAttribute("message", exception.getMessage());
        return "error";
    }
    
    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleInsufficientStockException(InsufficientStockException exception,Model model){
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(InvalidQuantityException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidQuantityException(InvalidQuantityException exception,Model model){
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(NoSelectedCartItemsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleNoSelectedCartItems(
            NoSelectedCartItemsException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(SellerAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleSellerAccessDeniedException(
            SellerAccessDeniedException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleOrderNotFound(OrderNotFoundException exception, Model model) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(PaymentCardNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handlePaymentCardNotFound(
            PaymentCardNotFoundException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleInsufficientFunds(
            InsufficientFundsException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(PaymentAlreadyProcessedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handlePaymentAlreadyProcessed(
            PaymentAlreadyProcessedException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(WalletAccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleWalletAccountNotFound(
            WalletAccountNotFoundException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler({
            InvalidOrderStateException.class,
            RefundNotAvailableException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleInvalidOrderState(
            RuntimeException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(OrderCancellationFailedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleOrderCancellationFailed(
            OrderCancellationFailedException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }

    @ExceptionHandler(OwnerWalletAccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleOwnerWalletAccountNotFound(
            OwnerWalletAccountNotFoundException exception,
            Model model
    ) {
        model.addAttribute("message", exception.getMessage());
        return "error";
    }
}
