package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.marketflow.AccountType;
import com.example.marketflow.Repository.UserRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.Repository.userRoleRepository;
import com.example.marketflow.User.LoginRequest;
import com.example.marketflow.User.RegisterRequest;
import com.example.marketflow.User.ShowUserDto;
import com.example.marketflow.User.UserEntity;
import com.example.marketflow.exception.EmailAlreadyExistsException;
import com.example.marketflow.exception.InvalidCredentialsException;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.userRoles.UserRolesEntity;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private userRoleRepository roleRepository;

    @Mock
    private WalletAccountRepository walletAccountRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerBuyerNormalizesEmailHashesPasswordAndAssignsBuyerRole() {
        RegisterRequest request = registerRequest(AccountType.BUYER);
        UserEntity savedUser = org.mockito.Mockito.mock(UserEntity.class);

        when(userRepository.existsByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("password-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(savedUser.getId()).thenReturn(7L);

        authService.register(request);

        ArgumentCaptor<UserEntity> userCaptor =
                ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("buyer@example.com", userCaptor.getValue().getEmail());
        assertEquals("password-hash", userCaptor.getValue().getPasswordHash());
        assertEquals("Ivan", userCaptor.getValue().getDisplayName());

        ArgumentCaptor<UserRolesEntity> roleCaptor =
                ArgumentCaptor.forClass(UserRolesEntity.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertEquals(7L, roleCaptor.getValue().getUserId());
        assertEquals((short) 1, roleCaptor.getValue().getRoleId());
        verify(walletAccountRepository, never()).save(any(WalletAccountEntity.class));
    }

    @Test
    void registerSellerAssignsBuyerAndSellerRolesImmediately() {
        RegisterRequest request = registerRequest(AccountType.SELLER);
        UserEntity savedUser = org.mockito.Mockito.mock(UserEntity.class);

        when(userRepository.existsByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("password-hash");
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(savedUser.getId()).thenReturn(7L);

        authService.register(request);

        ArgumentCaptor<UserRolesEntity> roleCaptor =
                ArgumentCaptor.forClass(UserRolesEntity.class);
        verify(roleRepository, org.mockito.Mockito.times(2)).save(roleCaptor.capture());
        assertEquals(java.util.List.of((short) 1, (short) 2),
                roleCaptor.getAllValues().stream().map(UserRolesEntity::getRoleId).toList());
        verify(walletAccountRepository).save(any(WalletAccountEntity.class));
    }

    @Test
    void registerRejectsExistingEmailBeforeWritingAnything() {
        RegisterRequest request = registerRequest(AccountType.BUYER);
        when(userRepository.existsByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.register(request)
        );

        verify(userRepository, never()).save(any(UserEntity.class));
        verifyNoInteractions(passwordEncoder, roleRepository, walletAccountRepository);
    }

    @Test
    void authenticateReturnsUserForNormalizedEmailAndCorrectPassword() {
        LoginRequest request = loginRequest(" Buyer@Example.com ", "password123");
        UserEntity user = new UserEntity(
                "buyer@example.com",
                "password-hash",
                "Ivan"
        );

        when(userRepository.findByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "password-hash"))
                .thenReturn(true);

        ShowUserDto result = authService.authenticate(request);

        assertEquals("buyer@example.com", result.getEmail());
        assertEquals("Ivan", result.getDisplayName());
        verify(passwordEncoder).matches("password123", "password-hash");
    }

    @Test
    void authenticateRejectsWrongPassword() {
        LoginRequest request = loginRequest("buyer@example.com", "wrong-password");
        UserEntity user = new UserEntity(
                "buyer@example.com",
                "password-hash",
                "Ivan"
        );

        when(userRepository.findByEmailIgnoreCase("buyer@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "password-hash"))
                .thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.authenticate(request)
        );
    }

    private RegisterRequest registerRequest(AccountType accountType) {
        RegisterRequest request = new RegisterRequest();
        request.setAccountType(accountType);
        request.setEmail(" Buyer@Example.com ");
        request.setPassword("password123");
        request.setDisplay_name(" Ivan ");
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
