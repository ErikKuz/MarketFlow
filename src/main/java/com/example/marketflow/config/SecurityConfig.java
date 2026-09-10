package com.example.marketflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import com.example.marketflow.security.ApiAccessDeniedHandler;
import com.example.marketflow.security.ApiAuthenticationEntryPoint;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {// Передаём UserDetailsService.
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider
    ) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {// Эта реализация сохраняет SecurityContext в HttpSession.
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {// После входа изменяет JSESSIONID.
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            com.example.marketflow.Repository.UserRepository users,
            com.example.marketflow.Repository.userRoleRepository roles
    ) throws Exception {
        http
                .securityMatcher("/api/**")
                .addFilterBefore(new com.example.marketflow.security.SessionAccountFilter(users, roles),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )
                .csrf(csrf -> {})
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers("/api/v1/seller/**").hasRole("SELLER")
                        .requestMatchers("/api/v1/wallet/**").hasRole("SELLER")
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/account/**").authenticated()
                        .requestMatchers(
                                "/api/v1/cart/**",
                                "/api/v1/checkout/**",
                                "/api/v1/orders/**",
                                "/api/v1/payment-cards/**"
                        ).hasRole("BUYER")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain mvcSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            com.example.marketflow.Repository.UserRepository users,
            com.example.marketflow.Repository.userRoleRepository roles
    ) throws Exception {
        http
                .addFilterBefore(new com.example.marketflow.security.SessionAccountFilter(users, roles),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )
                .csrf(csrf -> {})
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/css/**",
                                "/favicon.ico",
                                "/error",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers("/seller/**").hasRole("SELLER")
                        .requestMatchers("/workspace/seller/**").hasRole("SELLER")
                        .requestMatchers("/account/**", "/Buyer/**").hasRole("BUYER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/security-login")
                        .permitAll()
                );

        return http.build();
    }
}
