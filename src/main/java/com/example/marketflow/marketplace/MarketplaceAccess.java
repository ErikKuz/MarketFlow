package com.example.marketflow.marketplace;

import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import com.example.marketflow.Repository.UserRepository;
import com.example.marketflow.Repository.userRoleRepository;
import com.example.marketflow.User.UserStatus;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class MarketplaceAccess {
    private final UserRepository users;
    private final userRoleRepository roles;

    @Transactional(readOnly = true)
    public void require(Long userId, String... allowedRoles) {
        if (userId == null) throw new MarketplaceException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Sign in first");
        var user = users.findById(userId).orElseThrow(() ->
                new MarketplaceException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Account not found"));
        if (user.getStatus() != UserStatus.ACTIVE)
            throw new MarketplaceException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Account is not active");
        if (allowedRoles.length > 0 && Arrays.stream(allowedRoles).noneMatch(roles.findRoleNamesByUserId(userId)::contains))
            throw new MarketplaceException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Role does not allow this operation");
    }

    public static org.springframework.data.domain.Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new MarketplaceException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page must be >= 0 and size between 1 and 100");
        return org.springframework.data.domain.PageRequest.of(page, size);
    }
}
