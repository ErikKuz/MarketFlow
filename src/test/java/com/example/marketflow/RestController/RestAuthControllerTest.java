package com.example.marketflow.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.marketflow.AccountType;
import com.example.marketflow.User.RegisterRequest;
import com.example.marketflow.User.UserStatus;
import com.example.marketflow.exception.EmailAlreadyExistsException;
import com.example.marketflow.exception.GlobalRestExceptionHandler;
import com.example.marketflow.exception.InvalidCredentialsException;
import com.example.marketflow.security.MarketFlowPrincipal;
import com.example.marketflow.security.SessionAuthenticationService;
import com.example.marketflow.service.AuthService;

@WebMvcTest(RestAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalRestExceptionHandler.class)
public class RestAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionAuthenticationService sessionAuthenticationService;

    @Test
    void registerReturns201ForValidRequest() throws Exception {
        // Выполнение и проверка
        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountType": "BUYER",
                                  "email": "buyer@example.com",
                                  "password": "password123",
                                  "display_name": "Ivan"
                                }
                                """)
        )
        .andExpect(status().isCreated())
        .andExpect(content().string(""));

        // Проверяем DTO, переданный контроллером в сервис
        ArgumentCaptor<RegisterRequest> captor =
                ArgumentCaptor.forClass(RegisterRequest.class);

        verify(authService).register(captor.capture());

        RegisterRequest actualRequest = captor.getValue();

        assertEquals(
                AccountType.BUYER,
                actualRequest.getAccountType()
        );
        assertEquals(
                "buyer@example.com",
                actualRequest.getEmail()
        );
        assertEquals(
                "password123",
                actualRequest.getPassword()
        );
        assertEquals(
                "Ivan",
                actualRequest.getDisplay_name()
        );
    }

    @Test
    void registerReturns400ForInvalidEmail() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountType": "BUYER",
                                  "email": "not-an-email",
                                  "password": "password123",
                                  "display_name": "Ivan"
                                }
                                """)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.validationErrors[0].field").value("email"));

        verifyNoInteractions(authService);
    }

    @Test
    void registerReturns409WhenEmailAlreadyExists() throws Exception {
        doThrow(new EmailAlreadyExistsException("buyer@example.com"))
                .when(authService)
                .register(any(RegisterRequest.class));

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountType": "BUYER",
                                  "email": "buyer@example.com",
                                  "password": "password123",
                                  "display_name": "Ivan"
                                }
                                """)
        )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void loginReturnsAuthenticatedUser() throws Exception {
        var user = new com.example.marketflow.User.AuthenticatedUserDto(
                7L,
                "buyer@example.com",
                UserStatus.ACTIVE,
                "Ivan",
                false
        );
        MarketFlowPrincipal principal = mock(MarketFlowPrincipal.class);

        when(sessionAuthenticationService.login(any(), any(), any()))
                .thenReturn(principal);
        when(principal.toAuthenticatedUserDto()).thenReturn(user);

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "buyer@example.com",
                                  "password": "password123"
                                }
                                """)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.email").value("buyer@example.com"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.displayName").value("Ivan"))
        .andExpect(jsonPath("$.seller").value(false));

        verify(sessionAuthenticationService).login(any(), any(), any());
    }

    @Test
    void loginReturns401ForInvalidCredentials() throws Exception {
        when(sessionAuthenticationService.login(any(), any(), any()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "buyer@example.com",
                                  "password": "wrong-password"
                                }
                                """)
        )
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void logoutReturns204AndDelegatesToSessionSecurity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .session(session)
        )
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

        verify(sessionAuthenticationService).logout(any(), any(), any());
    }
}
