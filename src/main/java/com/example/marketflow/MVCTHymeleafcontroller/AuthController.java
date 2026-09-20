package com.example.marketflow.MVCTHymeleafcontroller;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.marketflow.User.LoginRequest;
import com.example.marketflow.User.RegisterRequest;
import com.example.marketflow.security.MarketFlowPrincipal;
import com.example.marketflow.security.SessionAuthenticationService;
import com.example.marketflow.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
public class AuthController {

    private final AuthService authservice;
    private final SessionAuthenticationService sessionAuthenticationService;

    public AuthController(
            AuthService authservice,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.authservice = authservice;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @GetMapping("/")
    public String authPage() {
        return "authPage";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "registerPage";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "loginPage";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegisterRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return "registerPage";
        }

        authservice.register(request);

        return "redirect:/login";
    }

    @PostMapping("/login")
    public String login(
            @Valid @ModelAttribute LoginRequest log,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response
    )
    {
        if (bindingResult.hasErrors()) {
            return "loginPage";
        }
        MarketFlowPrincipal principal = sessionAuthenticationService.login(
                log,
                request,
                response
        );
        return principal.isSeller()
                ? "redirect:/seller/account"
                : "redirect:/Buyer/account";
    }

}
