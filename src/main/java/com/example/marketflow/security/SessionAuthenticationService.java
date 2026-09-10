package com.example.marketflow.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import com.example.marketflow.User.LoginRequest;
import com.example.marketflow.exception.InvalidCredentialsException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionAuthenticationService {
//SessionAuthenticationService организует полный вход и выход через HTTP-сессию:
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    //Это встроенный интерфейс Spring Security для сохранения и последующего восстановления SecurityContext.
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    //Это встроенный интерфейс Spring Security, который выполняет действия с сессией после успешной аутентификации.
    //Его задача — защита от атаки с фиксацией идентификатора сессии.
    //До входа:
        // JSESSIONID = old123

        // После входа:
        // JSESSIONID = new789
    public MarketFlowPrincipal login(
            LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()
                    )
            );

            sessionAuthenticationStrategy.onAuthentication(
                    authentication,
                    httpRequest,
                    httpResponse
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            MarketFlowPrincipal principal = (MarketFlowPrincipal) authentication.getPrincipal();

            // Временно оставлено для существующих MVC- и REST-контроллеров.
            // Доступ к защищённым адресам по-прежнему определяет Spring Security.
            httpRequest.getSession().setAttribute("userId", principal.getUserId());

            return principal;
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }
    }

    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID")
                .logout(request, response, authentication);
        SecurityContextHolder.clearContext();
    }
}
