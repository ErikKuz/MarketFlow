package com.example.marketflow.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.AccountType;
import com.example.marketflow.Repository.UserRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.Repository.userRoleRepository;
import com.example.marketflow.User.LoginRequest;
import com.example.marketflow.User.RegisterRequest;
import com.example.marketflow.User.ShowUserDto;
import com.example.marketflow.User.UserEntity;
import com.example.marketflow.User.UserMapper;
import com.example.marketflow.User.UserStatus;
import com.example.marketflow.exception.EmailAlreadyExistsException;
import com.example.marketflow.exception.InvalidCredentialsException;
import com.example.marketflow.exception.UserNotFoundException;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.userRoles.UserRoleId;
import com.example.marketflow.userRoles.UserRolesEntity;

import lombok.AllArgsConstructor;


@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final userRoleRepository repository;
    private final WalletAccountRepository walletAccountRepository;

    @Transactional
    public void register(RegisterRequest userDto) {
        String email = userDto.getEmail()
                .toLowerCase()
                .trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        String passwordHash = passwordEncoder.encode(
                userDto.getPassword()
        );

        UserEntity savedUser = userRepository.save(
                UserMapper.convert(userDto, passwordHash)
        );

        repository.save(new UserRolesEntity(savedUser.getId(), (short) 1));
        if (userDto.getAccountType() == AccountType.SELLER) {
            repository.save(
                    new UserRolesEntity(
                            savedUser.getId(),
                            (short) 2
                    )
            );

            walletAccountRepository.save(
                    WalletAccountEntity.seller(
                            savedUser.getId()
                    )
            );
        }
    }

    @Transactional(readOnly = true)
    public ShowUserDto authenticate(LoginRequest log) {
        String email=log.getEmail().toLowerCase().trim();
        UserEntity user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        boolean passwordCorrect = passwordEncoder.matches(
                log.getPassword(),
                user.getPasswordHash()
        );

        if (!passwordCorrect) {
            throw new InvalidCredentialsException();
        }

        return UserMapper.toShowUserDto(user);
    }

    @Transactional(readOnly = true)
    public ShowUserDto getUserById(Long id){
        return userRepository.findById(id)
                .map(UserMapper::toShowUserDto)
                .orElseThrow(()-> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Boolean isSeller(Long id) {
        return repository.existsById(new UserRoleId(id, (short) 2));
    }
}
