package com.example.marketflow.MVCTHymeleafcontroller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.marketflow.payment_cards.AddPaymentCardRequest;
import com.example.marketflow.service.PaymentCardService;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;

@Controller
@RequestMapping("/account")
@AllArgsConstructor
public class PaymentCardController {
    private final PaymentCardService paymentCardService;

    @GetMapping("/account/cards")
    public String ShowCardUser(
            Model model,
            HttpSession session,
            @RequestParam(defaultValue = "false") boolean added
    ){
        model.addAttribute("cards", paymentCardService.getUserPaymentCards((Long)session.getAttribute("userId")));
        model.addAttribute("cardAdded", added);
        return "showCards";
    }

    @GetMapping("/cards/add")
    public String InterfaceAddCardUser(){
        return "addCardForm";
    }

    @PostMapping("/account/cards")
    public String AddCardUser(@ModelAttribute AddPaymentCardRequest dto,HttpSession session){
        paymentCardService.addPaymentCard((Long)session.getAttribute("userId"),dto);
        return "redirect:/account/account/cards?added=true";
    }
}
